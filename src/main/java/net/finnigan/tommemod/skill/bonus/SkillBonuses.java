package net.finnigan.tommemod.skill.bonus;

import net.finnigan.tommemod.skill.data.ModSkillCapabilities;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * The one call every gameplay handler makes.
 *
 * Deliberately the whole of the read side: a handler asks what a player's bonus is for one key and
 * gets a number. It never learns which skill paid for it, which node, or how many. That is what makes
 * the trees data - a designer can move a bonus between skills, split it over three nodes, or delete
 * it, and the handler carries on working unchanged (an absent bonus reads as zero, which is exactly
 * the "no effect" case the handler already had to cope with).
 */
public final class SkillBonuses {

    private SkillBonuses() {
    }

    /** A player's total for one bonus key, or zero for anything that isn't a player. */
    public static double get(Entity entity, ResourceLocation key) {
        if (!(entity instanceof Player player)) return 0.0;
        return player.getCapability(ModSkillCapabilities.SKILLS)
                .map(handler -> handler.bonus(key))
                .orElse(0.0);
    }

    /** Whether a player has any of a bonus at all - the cheap guard before doing real work. */
    public static boolean has(Entity entity, ResourceLocation key) {
        return get(entity, key) > 0.0;
    }

    /** A multiplier for bonuses expressed as "+x% of something": 0.15 reads back as 1.15. */
    public static double multiplier(Entity entity, ResourceLocation key) {
        return 1.0 + get(entity, key);
    }

    /** A bonus used as a probability, clamped so stacked nodes can't exceed certainty. */
    public static boolean roll(Entity entity, ResourceLocation key) {
        double chance = get(entity, key);
        return chance > 0.0 && entity.level().getRandom().nextDouble() < Math.min(chance, 1.0);
    }

    /** A fraction of something removed, clamped so stacked nodes can't invert the value. */
    public static double reduction(Entity entity, ResourceLocation key) {
        return Math.min(Math.max(get(entity, key), 0.0), 1.0);
    }
}
