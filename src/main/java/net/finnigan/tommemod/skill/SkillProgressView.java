package net.finnigan.tommemod.skill;

import net.minecraft.resources.ResourceLocation;

/**
 * Read-only view of one player's standing in every skill.
 *
 * Requirements are checked against this rather than against the capability itself, for two reasons:
 * the client has to be able to evaluate them (to grey out a node before the player clicks it) while
 * only holding a mirror of the data, and the server has to evaluate exactly the same expression
 * against the real thing when the click arrives. One interface, two backings, no drift.
 */
public interface SkillProgressView {

    /** Current level in a skill. Skills start at level 1, so an unknown skill reads as 1. */
    int level(ResourceLocation skill);

    /** Experience accumulated toward the next level. */
    double xp(ResourceLocation skill);

    /** Points spent inside this skill's tree. */
    int pointsSpent(ResourceLocation skill);

    /** Points earned in this skill and not yet spent. */
    int pointsAvailable(ResourceLocation skill);

    /** How many ranks of a node the player owns; 0 if they have never bought it. */
    int nodeRank(ResourceLocation skill, String nodeId);
}
