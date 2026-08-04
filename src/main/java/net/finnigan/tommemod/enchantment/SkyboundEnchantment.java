package net.finnigan.tommemod.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

/**
 * Skybound - elytra-only enchantment; holding sneak while gliding self-propels the player.
 *
 * WEARABLE is the closest vanilla category (there is no elytra category), so canEnchant narrows it
 * back down to the vanilla elytra specifically - Bee Wings are deliberately excluded even though
 * they're in the elytra_like tag and glide the same way.
 */
public class SkyboundEnchantment extends Enchantment {

    public SkyboundEnchantment() {
        super(Rarity.VERY_RARE, EnchantmentCategory.WEARABLE, new EquipmentSlot[]{EquipmentSlot.CHEST});
    }

    @Override
    public int getMaxLevel() {
        return 1;
    }

    @Override
    public int getMinCost(int level) {
        return 20;
    }

    @Override
    public int getMaxCost(int level) {
        return getMinCost(level) + 30;
    }

    @Override
    public boolean canEnchant(ItemStack stack) {
        return stack.is(Items.ELYTRA);
    }

    @Override
    public boolean isAllowedOnBooks() {
        return true;
    }
}
