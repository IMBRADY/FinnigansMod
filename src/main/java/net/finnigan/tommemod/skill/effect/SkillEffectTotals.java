package net.finnigan.tommemod.skill.effect;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Everything a player's purchased nodes add up to, gathered in one pass over the trees.
 *
 * Summing before applying is what keeps the attribute side cheap and correct: however many nodes
 * across however many skills push on movement speed, the player ends up wearing exactly one
 * modifier per attribute and operation. Nothing has to remember which node contributed what, so a
 * respec or a datapack reload is just another recompute.
 */
public class SkillEffectTotals implements SkillEffectSink {

    private final Map<Attribute, Map<AttributeModifier.Operation, Double>> attributes = new HashMap<>();
    private final Map<ResourceLocation, Double> bonuses = new HashMap<>();

    @Override
    public void addAttributeModifier(Attribute attribute, AttributeModifier.Operation operation, double amount) {
        if (amount == 0.0) return;
        attributes.computeIfAbsent(attribute, key -> new EnumMap<>(AttributeModifier.Operation.class))
                .merge(operation, amount, Double::sum);
    }

    @Override
    public void addBonus(ResourceLocation key, double amount) {
        if (amount == 0.0) return;
        bonuses.merge(key, amount, Double::sum);
    }

    public Map<Attribute, Map<AttributeModifier.Operation, Double>> attributes() {
        return attributes;
    }

    public Map<ResourceLocation, Double> bonuses() {
        return bonuses;
    }

    public double bonus(ResourceLocation key) {
        return bonuses.getOrDefault(key, 0.0);
    }
}
