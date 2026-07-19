package net.finnigan.tommemod.item.custom.totems;

import net.finnigan.tommemod.item.custom.ITotemEffect;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

public class TotemOfTheSunItem extends Item implements ITotemEffect {

    private static final UUID SPEED_ID = UUID.fromString("a1b2c3d4-0001-4a1a-9a1a-000000000001");
    private static final UUID DAMAGE_ID = UUID.fromString("a1b2c3d4-0002-4a1a-9a1a-000000000002");
    private static final UUID HEALTH_ID = UUID.fromString("a1b2c3d4-0003-4a1a-9a1a-000000000003");
    private static final UUID ARMOR_ID = UUID.fromString("a1b2c3d4-0004-4a1a-9a1a-000000000004");

    private static final double MAX_SPEED_BONUS = 0.2;   // in percentile
    private static final double MAX_DAMAGE_BONUS = 0.2;
    private static final double MAX_HEALTH_BONUS = 4.0;   // +2 hearts
    private static final double MAX_ARMOR_BONUS = 2.0;

    public TotemOfTheSunItem(Properties properties) {
        super(properties);
    }

    @Override
    public void onPlayerTick(Player player, ItemStack totemStack) {
        Level level = player.level();
        if (level.isClientSide) return;

        long dayTime = level.getDayTime() % 24000L;
        double strength;
        if (dayTime <= 12000L) {
            double distanceFromNoon = Math.abs(dayTime - 6000L) / 6000.0; // 0 at noon, 1 at sunrise/sunset
            strength = 1.0 - distanceFromNoon;
        } else {
            strength = 0.0; // nighttime — no buff
        }

        apply(player, Attributes.MOVEMENT_SPEED, SPEED_ID, "Totem of the Sun speed",
                MAX_SPEED_BONUS * strength, AttributeModifier.Operation.MULTIPLY_TOTAL);
        apply(player, Attributes.ATTACK_DAMAGE, DAMAGE_ID, "Totem of the Sun damage",
                MAX_DAMAGE_BONUS * strength, AttributeModifier.Operation.MULTIPLY_TOTAL);
        apply(player, Attributes.MAX_HEALTH, HEALTH_ID, "Totem of the Sun health",
                MAX_HEALTH_BONUS * strength, AttributeModifier.Operation.ADDITION);
        apply(player, Attributes.ARMOR, ARMOR_ID, "Totem of the Sun armor",
                MAX_ARMOR_BONUS * strength, AttributeModifier.Operation.ADDITION);
    }

    public static void clearModifiers(Player player) {
        remove(player, Attributes.MOVEMENT_SPEED, SPEED_ID);
        remove(player, Attributes.ATTACK_DAMAGE, DAMAGE_ID);
        remove(player, Attributes.MAX_HEALTH, HEALTH_ID);
        remove(player, Attributes.ARMOR, ARMOR_ID);
    }

    private static void apply(Player player, Attribute attribute, UUID id, String name,
                              double value, AttributeModifier.Operation op) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;
        instance.removeModifier(id);
        if (value > 0.0001) {
            instance.addTransientModifier(new AttributeModifier(id, name, value, op));
        }
    }

    private static void remove(Player player, Attribute attribute, UUID id) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) instance.removeModifier(id);
    }
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Grants buffs to ALL stats during daytime. Effectiveness increases closer to noon").withStyle(style -> style.withColor(0x9422AB)));
        tooltip.add(Component.literal("Accessory Item").withStyle(style -> style.withColor(0x5D156B)));
        // literal displays exactly as is, translatable grabs from json
    }
}