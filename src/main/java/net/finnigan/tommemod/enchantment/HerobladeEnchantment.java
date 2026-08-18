package net.finnigan.tommemod.enchantment;

import net.finnigan.tommemod.util.ModTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

/**
 * Heroblade - +15% damage per level against anything in {@code tommemod:bosses}.
 *
 * Blades, cleavers, daggers and uniques, by {@code tommemod:heroblade_weapons}. The category is
 * {@code WEAPON} so the anvil and the table agree about what kind of thing this is at all, and
 * {@link #canEnchant} narrows it from there - the three weapon shapes do not share a class, daggers
 * deliberately extending Item rather than SwordItem to escape the hardcoded sweep.
 */
public class HerobladeEnchantment extends Enchantment {

    public HerobladeEnchantment() {
        super(Rarity.RARE, EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    public int getMaxLevel() {
        return 5;
    }

    @Override
    public int getMinCost(int level) {
        return 8 + (level - 1) * 12;
    }

    @Override
    public int getMaxCost(int level) {
        return getMinCost(level) + 25;
    }

    @Override
    public boolean canEnchant(ItemStack stack) {
        return stack.is(ModTags.Items.HEROBLADE_WEAPONS) && super.canEnchant(stack);
    }

    @Override
    public boolean isAllowedOnBooks() {
        return true;
    }
}
