package net.finnigan.tommemod.skill.effect;

import com.google.gson.JsonObject;
import net.finnigan.tommemod.skill.RankedValue;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Locale;

/**
 * Moves a vanilla or Forge attribute - speed, health, armour, reach, and so on.
 *
 * The cheapest kind of node to write, because nothing anywhere has to know it exists: the totals are
 * folded into ordinary AttributeModifiers on the player, and the game applies them the same way it
 * applies a potion or a piece of armour.
 *
 * <pre>
 *   {"type": "tommemod:attribute", "attribute": "minecraft:generic.movement_speed",
 *    "operation": "multiply_base", "amount_per_rank": 0.05}
 * </pre>
 */
public record AttributeSkillEffect(Attribute attribute, AttributeModifier.Operation operation,
                                   RankedValue amount, ValueFormat format, String descriptionOverride)
        implements SkillEffect {

    @Override
    public void contribute(SkillEffectSink sink, int rank) {
        sink.addAttributeModifier(attribute, operation, amount.at(rank));
    }

    @Override
    public Component describe(int rank) {
        String value = format.format(amount.at(Math.max(rank, 1)));
        if (!descriptionOverride.isEmpty()) {
            return Component.literal(descriptionOverride.replace("%s", value)).withStyle(ChatFormatting.GRAY);
        }
        return Component.literal(value + " ")
                .append(Component.translatable(attribute.getDescriptionId()))
                .withStyle(ChatFormatting.GRAY);
    }

    public static SkillEffect parse(JsonObject json) {
        ResourceLocation attributeId = new ResourceLocation(GsonHelper.getAsString(json, "attribute"));
        Attribute attribute = ForgeRegistries.ATTRIBUTES.getValue(attributeId);
        if (attribute == null) {
            throw new IllegalArgumentException("Unknown attribute '" + attributeId + "'");
        }

        AttributeModifier.Operation operation =
                parseOperation(GsonHelper.getAsString(json, "operation", "addition"));
        // Multiplicative operations are fractions of a base value, so they only ever read sensibly as
        // a percentage; additive ones are raw points. Overridable, but right by default either way.
        ValueFormat fallback = operation == AttributeModifier.Operation.ADDITION
                ? ValueFormat.DECIMAL : ValueFormat.PERCENT;

        return new AttributeSkillEffect(attribute, operation, RankedValue.parseAmount(json),
                ValueFormat.byName(GsonHelper.getAsString(json, "format", ""), fallback),
                GsonHelper.getAsString(json, "description", ""));
    }

    private static AttributeModifier.Operation parseOperation(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "addition", "add" -> AttributeModifier.Operation.ADDITION;
            case "multiply_base" -> AttributeModifier.Operation.MULTIPLY_BASE;
            case "multiply_total" -> AttributeModifier.Operation.MULTIPLY_TOTAL;
            default -> throw new IllegalArgumentException("Unknown attribute operation '" + name + "'");
        };
    }
}
