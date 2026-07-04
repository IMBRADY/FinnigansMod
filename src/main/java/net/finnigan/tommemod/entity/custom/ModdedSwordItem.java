package net.finnigan.tommemod.entity.custom;

import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;

public class ModdedSwordItem extends SwordItem { // Add Swords here so its easier to override behavior like special hit effects

    public ModdedSwordItem(Tier tier, int attackDamageModifier, float attackSpeedModifier, Properties properties) {
        super(tier, attackDamageModifier, attackSpeedModifier, properties);
    }
}
