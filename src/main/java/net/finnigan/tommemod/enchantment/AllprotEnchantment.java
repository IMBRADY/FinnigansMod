package net.finnigan.tommemod.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

/**
 * Allprot - a chestplate that takes half of what lands on the players standing near its wearer, and
 * shrugs off four fifths of what it takes on their behalf.
 *
 * One level only. The share is a flat half rather than something that ranks up because two wearers
 * standing together already double-cover a party, and the handler deliberately does not let the
 * redirect chain - see {@code AllprotHandler}.
 */
public class AllprotEnchantment extends Enchantment {

    public AllprotEnchantment() {
        super(Rarity.RARE, EnchantmentCategory.ARMOR_CHEST, new EquipmentSlot[]{EquipmentSlot.CHEST});
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
        return 50;
    }

    @Override
    public boolean isAllowedOnBooks() {
        return true;
    }
}
