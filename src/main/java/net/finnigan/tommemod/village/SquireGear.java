package net.finnigan.tommemod.village;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;

/**
 * Deciding whether one piece of kit is an upgrade on another.
 *
 * <p>Squires only ever take a piece back off a Warrior to put a better one on, so "better" has to
 * mean something specific. It means what the piece is worth in a fight and nothing else - armor and
 * toughness for a piece of armor, attack damage for a weapon - read off the item's own attribute
 * modifiers rather than guessed from its material, so a modded sword and a vanilla one are compared
 * on the same terms.
 *
 * <p>Equal is not better. Chainmail and iron helmets are worth exactly the same two points of armor
 * in vanilla, so a village reaching tier 3 will not pull a Warrior's chainmail helmet off to swap in
 * an iron one - there would be nothing in it. That is the rule working, not a gap in it.
 */
public final class SquireGear {

    private SquireGear() {
    }

    /**
     * Whether {@code candidate} is worth more than what the Warrior is already wearing or holding in
     * this slot. An empty slot is beaten by anything.
     */
    public static boolean isStronger(ItemStack candidate, ItemStack current, EquipmentSlot slot) {
        if (candidate.isEmpty()) return false;
        if (current.isEmpty()) return true;
        return score(candidate, slot) > score(current, slot);
    }

    /**
     * What a piece is worth in this slot. Armor and weapons are scored on different axes and never
     * compared against each other - nothing ever asks whether a sword beats a chestplate, because
     * they cannot occupy the same slot.
     */
    private static double score(ItemStack stack, EquipmentSlot slot) {
        if (slot.isArmor()) {
            double defense = stack.getItem() instanceof ArmorItem armor ? armor.getDefense() : 0;
            // Toughness is worth far less than a point of armor but has to break ties somewhere, and
            // between two pieces of equal armor it is the one that genuinely takes less damage.
            double toughness = stack.getItem() instanceof ArmorItem armor ? armor.getToughness() : 0;
            return defense + toughness * 0.1;
        }
        return attackDamage(stack, slot);
    }

    /**
     * Sum of an item's own additive attack damage modifiers in this slot. Only ADDITION is counted: a
     * percentage modifier is a share of a base this method has no business assuming, and every vanilla
     * weapon - and every one of this mod's - states its damage additively anyway.
     */
    private static double attackDamage(ItemStack stack, EquipmentSlot slot) {
        Collection<AttributeModifier> modifiers =
                stack.getAttributeModifiers(slot).get(Attributes.ATTACK_DAMAGE);
        double total = 0;
        for (AttributeModifier modifier : modifiers) {
            if (modifier.getOperation() == AttributeModifier.Operation.ADDITION) total += modifier.getAmount();
        }
        return total;
    }
}
