package net.finnigan.tommemod.item.custom;

import net.finnigan.tommemod.entity.custom.ColletisHelpers.ColletisVineEntity;
import net.finnigan.tommemod.event.ColletisEventHelpers.ColletisPassiveHandler;
import net.finnigan.tommemod.event.UniqueSwordEnforcementHandler;
import net.finnigan.tommemod.item.custom.totems.TotemUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Colletis: left click = 25 dmg (vanilla SwordItem base). Right-click (hold) grows a thorn vine:
 *  - hits an enemy -> pulls the enemy toward the player; releasing while it's still pulling lets the enemy go.
 *  - hits a block -> grapples the player toward the block; releasing stops the pull.
 *  - releasing while the vine is still extending outward (not yet stuck) just retracts it harmlessly.
 * Shift+click: instant spore burst (self/nearby Regeneration I + accelerated debuff decay + particles),
 * handled by ColletisPassiveHandler. Continuous crop-growth-while-crouching passive is a separate per-tick
 * handler in the same class (not routed through use()/useOn()).
 */
public class ColletisItem extends SwordItem {

    private static final Map<UUID, Integer> ACTIVE_VINES = new HashMap<>();
    private static final int VINE_COOLDOWN_TICKS = 20; // 1s, applied on release (matches Arackopesh's timing)

    private static final int SPORE_BURST_COOLDOWN_TICKS = 100; // 5s

    public ColletisItem(Tier tier, int attackDamage, float attackSpeed, Properties properties) {
        super(tier, attackDamage, attackSpeed, properties);
    }

    @Override
    public boolean isDamageable(ItemStack stack) {
        return false;
    }

    public static boolean isHeldBy(Player player) {
        return player.getMainHandItem().getItem() instanceof ColletisItem
                || player.getOffhandItem().getItem() instanceof ColletisItem;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 200; // 10s hard ceiling; real "release" is the player letting go, not reaching this
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPEAR;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entityLiving, int timeLeft) {
        if (entityLiving instanceof Player player && !level.isClientSide) {
            detachVine(player);
        }
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        if (player.isShiftKeyDown()) {
            return performSporeBurst(context.getLevel(), player, context.getHand()).getResult();
        }
        return performVineStart(context.getLevel(), player, context.getHand()).getResult();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player.isShiftKeyDown()) {
            return performSporeBurst(level, player, hand);
        }
        return performVineStart(level, player, hand);
    }

    private InteractionResultHolder<ItemStack> performVineStart(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player.getCooldowns().isOnCooldown(this) || ACTIVE_VINES.containsKey(player.getUUID())) {
            return InteractionResultHolder.pass(stack);
        }
        if (!UniqueSwordEnforcementHandler.canUseUniqueSword(player, this)) {
            return InteractionResultHolder.pass(stack);
        }

        if (!level.isClientSide) {
            ColletisVineEntity vine = new ColletisVineEntity(level, player);
            Vec3 look = player.getLookAngle();
            vine.setPos(player.getX() + look.x, player.getEyeY() - 0.2, player.getZ() + look.z);
            vine.shoot(look.x, look.y, look.z, 2.2F, 0.5F);
            level.addFreshEntity(vine);
            ACTIVE_VINES.put(player.getUUID(), vine.getId());

            player.startUsingItem(hand);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 1.0F, 0.9F);
        }

        player.swing(hand);
        return InteractionResultHolder.consume(stack);
    }

    private void detachVine(Player player) {
        Integer vineId = ACTIVE_VINES.get(player.getUUID());
        if (vineId != null && player.level() instanceof ServerLevel serverLevel) {
            Entity entity = serverLevel.getEntity(vineId);
            if (entity != null) entity.discard(); // triggers ColletisVineEntity.remove() -> cleans up the map
        }
        player.getCooldowns().addCooldown(this, TotemUtil.applyCooldownReduction(player, VINE_COOLDOWN_TICKS));
    }

    public static void clearVineFor(UUID ownerUUID) {
        ACTIVE_VINES.remove(ownerUUID);
    }

    private InteractionResultHolder<ItemStack> performSporeBurst(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.pass(stack);
        }
        if (!UniqueSwordEnforcementHandler.canUseUniqueSword(player, this)) {
            return InteractionResultHolder.pass(stack);
        }
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }

        ColletisPassiveHandler.performSporeBurst((ServerLevel) level, player);

        player.getCooldowns().addCooldown(this, TotemUtil.applyCooldownReduction(player, SPORE_BURST_COOLDOWN_TICKS));
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.AZALEA_LEAVES_BREAK, SoundSource.PLAYERS, 1.0F, 1.0F);

        player.swing(hand);
        return InteractionResultHolder.sidedSuccess(stack, false);
    }
}
