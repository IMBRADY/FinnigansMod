package net.finnigan.tommemod.item.custom;

import net.finnigan.tommemod.entity.custom.LanternaHelpers.LanternaChainEntity;
import net.finnigan.tommemod.event.UniqueSwordEnforcementHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LanternaItem extends SwordItem {
    private static final String FIRED_TAG = "LanternaFired";
    public static final String NEEDS_RELEASE_TAG = "LanternaNeedsRelease";
    private static final Map<UUID, Integer> ACTIVE_CHAINS = new HashMap<>();

    public LanternaItem(Tier tier, int attackDamage, float attackSpeed, Properties properties) {
        super(tier, attackDamage, attackSpeed, properties);
    }

    @Override
    public boolean isDamageable(ItemStack stack) {
        return false;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getOrCreateTag().getBoolean(NEEDS_RELEASE_TAG)
                || ACTIVE_CHAINS.containsKey(player.getUUID())) {
            return InteractionResultHolder.pass(stack);
        }
        if (!UniqueSwordEnforcementHandler.canUseUniqueSword(player, this)) {
            return InteractionResultHolder.pass(stack);
        }

        player.startUsingItem(hand);
        player.swing(hand);
        if (!level.isClientSide) {
            UUID shotId = UUID.randomUUID();
            stack.getOrCreateTag().putBoolean(FIRED_TAG, true);
            stack.getOrCreateTag().putUUID("LanternaShot", shotId);

            LanternaChainEntity chain = new LanternaChainEntity(level, player, shotId);
            Vec3 look = player.getLookAngle();
            chain.setPos(player.getX() + look.x, player.getEyeY() - 0.2, player.getZ() + look.z);
            chain.shoot(look.x, look.y, look.z, 2.2F, 0.0F);
            level.addFreshEntity(chain);
            ACTIVE_CHAINS.put(player.getUUID(), chain.getId());
            level.playSound(null, player.blockPosition(), SoundEvents.FISHING_BOBBER_THROW,
                    SoundSource.PLAYERS, 1.0F, 0.75F);
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity living, int timeLeft) {
        if (!(living instanceof Player player) || level.isClientSide) return;
        Integer id = ACTIVE_CHAINS.get(player.getUUID());
        if (id != null && level instanceof ServerLevel serverLevel) {
            Entity chain = serverLevel.getEntity(id);
            if (chain instanceof LanternaChainEntity lanternaChain) lanternaChain.startRetract();
        }
    }

    public static void clearChain(Player owner, UUID shotId, int chainId) {
        ACTIVE_CHAINS.computeIfPresent(owner.getUUID(),
                (uuid, activeId) -> activeId == chainId ? null : activeId);
        clearFired(owner.getMainHandItem(), shotId);
        clearFired(owner.getOffhandItem(), shotId);
        for (ItemStack stack : owner.getInventory().items) clearFired(stack, shotId);
    }

    public static void requireFreshClick(Player owner, UUID shotId) {
        markReleaseRequired(owner.getMainHandItem(), shotId);
        markReleaseRequired(owner.getOffhandItem(), shotId);
        for (ItemStack stack : owner.getInventory().items) markReleaseRequired(stack, shotId);
    }

    private static void markReleaseRequired(ItemStack stack, UUID shotId) {
        if (!(stack.getItem() instanceof LanternaItem) || !stack.hasTag()) return;
        if (stack.getTag().hasUUID("LanternaShot") && shotId.equals(stack.getTag().getUUID("LanternaShot"))) {
            stack.getTag().putBoolean(NEEDS_RELEASE_TAG, true);
        }
    }

    public static void clearReleaseLatch(Player player) {
        clearReleaseLatch(player.getMainHandItem());
        clearReleaseLatch(player.getOffhandItem());
        for (ItemStack stack : player.getInventory().items) clearReleaseLatch(stack);
    }

    private static void clearReleaseLatch(ItemStack stack) {
        if (stack.getItem() instanceof LanternaItem && stack.hasTag()) {
            stack.getTag().remove(NEEDS_RELEASE_TAG);
        }
    }

    private static void clearFired(ItemStack stack, UUID shotId) {
        if (!(stack.getItem() instanceof LanternaItem) || !stack.hasTag()) return;
        if (stack.getTag().hasUUID("LanternaShot") && shotId.equals(stack.getTag().getUUID("LanternaShot"))) {
            stack.getTag().remove(FIRED_TAG);
            stack.getTag().remove("LanternaShot");
        }
    }
}
