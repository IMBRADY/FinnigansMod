package net.finnigan.tommemod.item.custom;

import net.finnigan.tommemod.event.UniqueSwordEnforcementHandler;
import net.finnigan.tommemod.item.custom.CandeliereHelpers.CandeliereFlareManager;
import net.finnigan.tommemod.item.custom.totems.TotemUtil;
import net.minecraft.server.level.ServerLevel;
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
 * Candeliere: left click deals the base sword damage (25, handled automatically by vanilla SwordItem).
 * Right click looses three firework-like flares - no projectile entity, just trails of flame particles
 * that burst on contact, setting what they hit alight for 20 damage (see CandeliereFlareManager).
 * Passive: Purifying Light while held; melee hits lengthen an ability-lit fire by 10% each up to +50%
 * (see CandeliereBurnTracker); and this weapon hits burning enemies 20% harder
 * (see CandeliereCombatHandler).
 */
public class CandeliereItem extends SwordItem {

    private static final int COOLDOWN_TICKS = 35; // 1.75 seconds

    /** Extra damage this weapon deals to targets that are already on fire. */
    public static final float BURNING_TARGET_DAMAGE_MULTIPLIER = 1.20F;

    public CandeliereItem(Tier tier, int attackDamage, float attackSpeed, Properties properties) {
        super(tier, attackDamage, attackSpeed, properties);
    }

    @Override
    public boolean isDamageable(ItemStack stack) {
        return false;
    }

    public static boolean isHeldBy(Player player) {
        return player.getMainHandItem().getItem() instanceof CandeliereItem
                || player.getOffhandItem().getItem() instanceof CandeliereItem;
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
            CandeliereFlareManager.fireVolley((ServerLevel) level, player);

            player.getCooldowns().addCooldown(this, TotemUtil.applyCooldownReduction(player, COOLDOWN_TICKS));
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.0F, 1.1F);
        }

        player.swing(hand);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
