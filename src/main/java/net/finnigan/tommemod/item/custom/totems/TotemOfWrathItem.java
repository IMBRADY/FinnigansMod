package net.finnigan.tommemod.item.custom.totems;

import net.finnigan.tommemod.item.custom.ITotemEffect;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public class TotemOfWrathItem extends Item implements ITotemEffect {

    private static final UUID DAMAGE_ID = UUID.fromString("c1b2c3d4-0001-4a1a-9a1a-000000000009");
    private static final UUID HEALTH_ID = UUID.fromString("c1b2c3d4-0002-4a1a-9a1a-00000000000a");

    private static final double DAMAGE_MULT = 0.40;   // +40%
    private static final double HEALTH_MULT = -0.30;  // -30%

    public TotemOfWrathItem(Properties properties) {
        super(properties);
    }

    @Override
    public void onPlayerTick(Player player, ItemStack totemStack) {
        if (player.level().isClientSide) return;
        apply(player, Attributes.ATTACK_DAMAGE, DAMAGE_ID, "Totem of Wrath damage", DAMAGE_MULT);
        apply(player, Attributes.MAX_HEALTH, HEALTH_ID, "Totem of Wrath health", HEALTH_MULT);
    }

    public static void clearModifiers(Player player) {
        remove(player, Attributes.ATTACK_DAMAGE, DAMAGE_ID);
        remove(player, Attributes.MAX_HEALTH, HEALTH_ID);
    }

    private static void apply(Player player, Attribute attribute, UUID id, String name, double mult) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;
        if (instance.getModifier(id) != null) return; // already applied, avoid churn
        instance.addTransientModifier(new AttributeModifier(id, name, mult, AttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    private static void remove(Player player, Attribute attribute, UUID id) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) instance.removeModifier(id);
    }
}