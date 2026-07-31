package net.finnigan.tommemod.item.custom;

import net.finnigan.tommemod.event.UniqueSwordEnforcementHandler;
import net.finnigan.tommemod.item.custom.LumapierHelpers.LightBoltManager;
import net.finnigan.tommemod.item.custom.totems.TotemUtil;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.level.Level;

/**
 * Lumapier: left click deals the base sword damage (25, handled automatically by vanilla SwordItem).
 * Right click fires "Light Spray": 5 fast light-bolt projectiles spat out over ~1 second (4-tick gaps),
 * 8 damage each, via the same scheduled-steps idiom as War Flammer's Fire Wave.
 * While the spray is actively firing, the item's own icon swaps from lumapier_0 (idle) to lumapier_1
 * (firing) - see {@link #isFiring(ItemStack, Level)}, mirroring BlossomKatanaItem's aura-active NBT check.
 * Passive: while held (main or offhand), grants Purifying Light (poison/wither/nausea immunity + torch
 * light) and a flat +20% movement speed AttributeModifier (see LumapierPassiveHandler).
 */
public class LumapierItem extends SwordItem {

    private static final int COOLDOWN_TICKS = 80; // 4 seconds
    private static final int SPRAY_DURATION_TICKS = 20; // ~1 second, matches LightBoltManager's 5-shot/4-tick-gap spray
    private static final String TAG_FIRING_END_TIME = "FiringEndTime";

    public LumapierItem(Tier tier, int attackDamage, float attackSpeed, Properties properties) {
        super(tier, attackDamage, attackSpeed, properties);
    }

    @Override
    public boolean isDamageable(ItemStack stack) {
        return false;
    }

    public static boolean isHeldBy(Player player) {
        return player.getMainHandItem().getItem() instanceof LumapierItem
                || player.getOffhandItem().getItem() instanceof LumapierItem;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.pass(stack);
        }
        if (!UniqueSwordEnforcementHandler.canUseUniqueSword(player, this)) {
            return InteractionResultHolder.pass(stack);
        }

        if (!level.isClientSide) {
            LightBoltManager.startSpray(level, player);

            player.getCooldowns().addCooldown(this, TotemUtil.applyCooldownReduction(player, COOLDOWN_TICKS));
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.6F, 1.6F);

            // Stamp the "firing until" timestamp so the client can read it for the item-icon texture swap.
            stack.getOrCreateTag().putLong(TAG_FIRING_END_TIME, level.getGameTime() + SPRAY_DURATION_TICKS);
        }

        player.swing(hand);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** True while the spray is actively firing - drives the lumapier_0/lumapier_1 item-model swap. */
    public static boolean isFiring(ItemStack stack, Level level) {
        if (level == null || !stack.hasTag()) return false;
        long endTime = stack.getOrCreateTag().getLong(TAG_FIRING_END_TIME);
        return level.getGameTime() < endTime;
    }
}
