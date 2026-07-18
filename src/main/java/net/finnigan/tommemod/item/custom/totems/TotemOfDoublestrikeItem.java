package net.finnigan.tommemod.item.custom.totems;

import net.finnigan.tommemod.item.custom.ITotemEffect;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

public class TotemOfDoublestrikeItem extends Item implements ITotemEffect {

    private static final float DOUBLE_STRIKE_CHANCE = 0.10F;

    public TotemOfDoublestrikeItem(Properties properties) {
        super(properties);
    }

    /** Called from the attacker's perspective in LivingHurtEvent — see event hook below. */
    public boolean rollDoubleStrike(Player attacker) {
        return attacker.getRandom().nextFloat() < DOUBLE_STRIKE_CHANCE;
    }
}