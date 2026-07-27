package net.finnigan.tommemod.item.custom;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Tier;

/**
 * The Warrior Villager's starting weapon: a two-handed polearm. Subclasses PikeItem so it inherits
 * the close-range damage penalty and anvil enchant-merge rules already gated on `instanceof PikeItem`
 * elsewhere in the mod, rather than duplicating that logic.
 */
public class HalberdItem extends PikeItem {

    public HalberdItem(Tier tier, int attackDamageModifier, float attackSpeedModifier,
                        double reachBonus, float knockbackBonus, Item.Properties properties) {
        super(tier, attackDamageModifier, attackSpeedModifier, reachBonus, knockbackBonus, properties);
    }
}
