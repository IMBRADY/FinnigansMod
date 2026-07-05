package net.finnigan.tommemod.item.custom;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;

public class LongbowItem extends BowItem {
    public LongbowItem(Properties properties) {
        super(properties);
    }
    @Override
    public int getUseDuration(ItemStack stack) {
        return 80; // Max time you can hold bow
    }
    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player)) return;

        boolean hasInfinity = player.getAbilities().instabuild
                || EnchantmentHelper.getItemEnchantmentLevel(Enchantments.INFINITY_ARROWS, stack) > 0;

        ItemStack arrowStack = player.getProjectile(stack);
        if (arrowStack.isEmpty() && !hasInfinity) return;

        if (arrowStack.isEmpty()) {
            arrowStack = new ItemStack(Items.ARROW);
        }

        int chargeTicks = this.getUseDuration(stack) - timeLeft;
        float power = Mth.clamp((float) chargeTicks / 50.0F, 0.0F, 1.0F); // your custom curve

        if (power < 0.1F) return;

        boolean isCreativeArrow = arrowStack.getItem() instanceof ArrowItem;
        if (!level.isClientSide) {
            ArrowItem arrowItem = (ArrowItem) (isCreativeArrow ? arrowStack.getItem() : Items.ARROW);
            AbstractArrow arrow = arrowItem.createArrow(level, arrowStack, player);
            arrow = customArrow(arrow);
            arrow.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, power * 3.0F, 1.0F);

            if (power == 1.0F) arrow.setCritArrow(true);

            int power_ench = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.POWER_ARROWS, stack);
            if (power_ench > 0) arrow.setBaseDamage(arrow.getBaseDamage() + power_ench * 0.5 + 0.5);

            int punch_ench = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.PUNCH_ARROWS, stack);
            if (punch_ench > 0) arrow.setKnockback(punch_ench);

            if (EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FLAMING_ARROWS, stack) > 0) {
                arrow.setSecondsOnFire(100);
            }

            stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(player.getUsedItemHand()));

            if (isCreativeArrow || (player.getAbilities().instabuild && (arrowStack.is(Items.SPECTRAL_ARROW) || arrowStack.is(Items.TIPPED_ARROW)))) {
                arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
            }

            level.addFreshEntity(arrow);
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0F,
                1.0F / (level.getRandom().nextFloat() * 0.4F + 1.2F) + power * 0.5F);

        if (!hasInfinity && !player.getAbilities().instabuild) {
            arrowStack.shrink(1);
            if (arrowStack.isEmpty()) {
                player.getInventory().removeItem(arrowStack);
            }
        }

        player.awardStat(Stats.ITEM_USED.get(this));
    }
}
