package net.finnigan.tommemod.skill.curve;

/**
 * How much work one level of a skill costs.
 *
 * Expressed per-step rather than as a running total, because that is the number the player actually
 * sees ("1,240 / 1,800 to Agility 8") and the only one the progress bar needs.
 */
public interface XpCurve {

    /** Experience needed to go from {@code level} to {@code level + 1}. Levels start at 1. */
    double xpToAdvance(int level);
}
