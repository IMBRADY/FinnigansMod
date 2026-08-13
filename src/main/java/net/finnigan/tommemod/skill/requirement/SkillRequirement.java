package net.finnigan.tommemod.skill.requirement;

import net.minecraft.network.chat.Component;

/**
 * One condition standing between a player and a node.
 *
 * Requirements compose - {@code and}, {@code or} and {@code not} are themselves requirements - so a
 * tree file can state "Agility 10 AND Sprint Mastery 3" without any of it reaching Java. Nothing in
 * here knows what a node <em>is</em>; it only reads numbers out of a {@link SkillContext}.
 */
public interface SkillRequirement {

    boolean test(SkillContext context);

    /**
     * How this condition reads in the node's detail panel, with the player's live progress folded in
     * ("3 points invested in Agility (2/3)"). The screen colours the line by {@link #test}, so the
     * text itself never has to state whether it is met.
     */
    Component describe(SkillContext context);
}
