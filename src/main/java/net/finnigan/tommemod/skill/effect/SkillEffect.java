package net.finnigan.tommemod.skill.effect;

import net.minecraft.network.chat.Component;

/**
 * One thing a node does, at whatever rank the player has it.
 *
 * There are exactly two implementations and there is not meant to be a third: a node either moves an
 * attribute or raises a named bonus. Anything a designer wants a node to do is expressed as one of
 * those, which is why no node id ever appears in Java.
 */
public interface SkillEffect {

    /** Reports this effect's contribution at the given rank. Never called with rank 0. */
    void contribute(SkillEffectSink sink, int rank);

    /** One line for the node's tooltip, phrased for the rank the player is looking at. */
    Component describe(int rank);
}
