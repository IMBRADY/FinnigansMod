package net.finnigan.tommemod.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

/**
 * Immunity - armor enchantment that halves the power and duration of incoming negative effects,
 * once per enchanted armor piece (so a full set lands at 0.5^4 = 0.0625). Applied by
 * LivingEntityImmunityMixin via ImmunityEffectReducer.
 *
 * Deliberately unobtainable from enchanting tables, loot, or librarians - the only source is the
 * Beekeeper's master-level trade in ModVillagerTrades.
 */
public class ImmunityEnchantment extends Enchantment {

    public ImmunityEnchantment() {
        super(Rarity.VERY_RARE, EnchantmentCategory.ARMOR, new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET});
    }

    @Override
    public int getMaxLevel() {
        return 1;
    }

    @Override
    public int getMinCost(int level) {
        return 25;
    }

    @Override
    public int getMaxCost(int level) {
        return getMinCost(level) + 50;
    }

    @Override
    public boolean isTreasureOnly() {
        return true;
    }

    @Override
    public boolean isDiscoverable() {
        return false;
    }

    @Override
    public boolean isTradeable() {
        return false;
    }

    @Override
    public boolean isAllowedOnBooks() {
        return true;
    }
}
