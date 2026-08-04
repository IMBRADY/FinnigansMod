package net.finnigan.tommemod.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

/**
 * Glow - helmet enchantment that lights the area around whoever carries it.
 *
 * The slot list deliberately includes the hands as well as the head: GlowLightHandler resolves the
 * level with EnchantmentHelper, which only looks at the slots declared here, and the enchantment is
 * meant to work equally when the helmet is worn or just held. Ground items are handled separately
 * (an ItemEntity has no equipment slots at all).
 */
public class GlowEnchantment extends Enchantment {

    public GlowEnchantment() {
        super(Rarity.UNCOMMON, EnchantmentCategory.ARMOR_HEAD,
                new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND});
    }

    @Override
    public int getMaxLevel() {
        return 1;
    }

    @Override
    public int getMinCost(int level) {
        return 10;
    }

    @Override
    public int getMaxCost(int level) {
        return getMinCost(level) + 30;
    }

    @Override
    public boolean isAllowedOnBooks() {
        return true;
    }
}
