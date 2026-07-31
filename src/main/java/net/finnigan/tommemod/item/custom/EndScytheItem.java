package net.finnigan.tommemod.item.custom;

import net.finnigan.tommemod.entity.custom.EndScytheHelpers.EndScytheProjectileEntity;
import net.finnigan.tommemod.event.UniqueSwordEnforcementHandler;
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
 * End Scythe: left click deals the base sword damage (25, handled automatically by vanilla SwordItem).
 * Right click fires a spinning, fast, wall-piercing projectile that homes toward the nearest player
 * (excluding the shooter), traveling up to 32 blocks and dealing 16 damage on hit.
 * Passive: nearby Endermen defend the holder like tamed wolves defend their owner; while in the End,
 * 1.2x max HP and 1.2x movement speed (see EndScythePassiveHandler / EndScytheEndermanAllyHandler).
 */
public class EndScytheItem extends SwordItem {

    private static final int COOLDOWN_TICKS = 60; // 3 seconds

    public EndScytheItem(Tier tier, int attackDamage, float attackSpeed, Properties properties) {
        super(tier, attackDamage, attackSpeed, properties);
    }

    @Override
    public boolean isDamageable(ItemStack stack) {
        return false;
    }

    public static boolean isHeldBy(Player player) {
        return player.getMainHandItem().getItem() instanceof EndScytheItem
                || player.getOffhandItem().getItem() instanceof EndScytheItem;
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
            EndScytheProjectileEntity projectile = new EndScytheProjectileEntity(level, player);
            level.addFreshEntity(projectile);

            player.getCooldowns().addCooldown(this, TotemUtil.applyCooldownReduction(player, COOLDOWN_TICKS));
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 0.8F);
        }

        return InteractionResultHolder.success(stack);
    }
}
