package net.finnigan.tommemod.skill.xp;

/**
 * A test an action must pass before an XP source pays out - "only coal ore", "only with an axe",
 * "only undead". Lets one action id feed several skills at different rates without any of them
 * needing their own event handler.
 */
public interface SkillFilter {
    boolean test(SkillAction action);
}
