package net.finnigan.tommemod.item.custom;

import net.finnigan.tommemod.event.UniqueSwordEnforcementHandler;
import net.finnigan.tommemod.item.custom.CustodireGladioHelpers.ShieldWallManager;
import net.finnigan.tommemod.item.custom.totems.TotemUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.level.Level;

/**
 * Custodire Gladio: left click deals the base sword damage (25, handled automatically by vanilla
 * SwordItem). Right click raises a 3-block-wide shield wall in front of the wielder that eats
 * projectiles from either side and sustains anyone sheltering behind it (see ShieldWallManager).
 * Passive: max health, melee damage, and the wall's own numbers all scale with the wielder's
 * reputation in the village they are Chief of - +20% per tier above Novice (see ChiefTierResolver
 * and CustodireGladioPassiveHandler).
 */
public class CustodireGladioItem extends SwordItem {

    private static final int COOLDOWN_TICKS = 240; // 12 seconds

    public CustodireGladioItem(Tier tier, int attackDamage, float attackSpeed, Properties properties) {
        super(tier, attackDamage, attackSpeed, properties);
    }

    @Override
    public boolean isDamageable(ItemStack stack) {
        return false;
    }

    public static boolean isHeldBy(Player player) {
        return player.getMainHandItem().getItem() instanceof CustodireGladioItem
                || player.getOffhandItem().getItem() instanceof CustodireGladioItem;
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
            ShieldWallManager.deploy((ServerLevel) level, player);
            player.getCooldowns().addCooldown(this, TotemUtil.applyCooldownReduction(player, COOLDOWN_TICKS));
        }

        player.swing(hand);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
