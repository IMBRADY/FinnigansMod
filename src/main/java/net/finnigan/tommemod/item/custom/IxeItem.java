package net.finnigan.tommemod.item.custom;

import net.finnigan.tommemod.entity.custom.IxeHelpers.IxeProjectileEntity;
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
 * Ixe: right click fires a spinning AOE projectile that freezes anything it hits (4 dmg/sec for 2s).
 * Passive: Regeneration I in snowy biomes, 1.5x speed while snowing, never freezes.
 */
public class IxeItem extends SwordItem {

    private static final int COOLDOWN_TICKS = 240; // 12 seconds

    public IxeItem(Tier tier, int attackDamage, float attackSpeed, Properties properties) {
        super(tier, attackDamage, attackSpeed, properties);
    }

    @Override
    public boolean isDamageable(ItemStack stack) {
        return false;
    }

    public static boolean isHeldBy(Player player) {
        return player.getMainHandItem().getItem() instanceof IxeItem
                || player.getOffhandItem().getItem() instanceof IxeItem;
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
            IxeProjectileEntity projectile = new IxeProjectileEntity(level, player);
            level.addFreshEntity(projectile);

            player.getCooldowns().addCooldown(this, TotemUtil.applyCooldownReduction(player, COOLDOWN_TICKS));
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0F, 1.4F);
        }

        return InteractionResultHolder.success(stack);
    }
}
