package net.finnigan.tommemod.skill.effect;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * What a purchased node hands back when it is asked what it does.
 *
 * Deliberately the only channel an effect has to the game: an effect cannot touch the player, only
 * describe itself in these two currencies. That is what keeps a tree file from ever needing code -
 * a node grants either a vanilla attribute or a named bonus, and both are already understood.
 */
public interface SkillEffectSink {

    /** Contributes to a vanilla/Forge attribute. Contributions to the same attribute and operation
     * from every node in every tree are summed into a single modifier before being applied. */
    void addAttributeModifier(Attribute attribute, AttributeModifier.Operation operation, double amount);

    /** Contributes to a named bonus, read back later by whichever handler owns that key
     * (see ModSkillBonuses). Values under the same key sum. */
    void addBonus(ResourceLocation key, double amount);
}
