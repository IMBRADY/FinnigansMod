package net.finnigan.tommemod.skill.xp;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.util.List;

/**
 * One way a skill earns experience: an action to listen for, conditions it has to meet, and what it
 * pays.
 *
 * <pre>
 *   {"action": "tommemod:distance/sprint", "xp_per_unit": 0.4}
 *   {"action": "tommemod:block_broken", "xp": 12,
 *    "filters": [{"type": "tommemod:block_tag", "tag": "forge:ores"}]}
 * </pre>
 *
 * {@code xp} is a flat payment for the action happening at all; {@code xp_per_unit} is multiplied by
 * the action's amount (blocks travelled, damage dealt). A source may use either or both.
 */
public record XpSource(ResourceLocation action, double flatXp, double xpPerUnit, List<SkillFilter> filters) {

    /** What this source pays for the given action, or zero if it has nothing to say about it. */
    public double xpFor(SkillAction candidate) {
        if (!candidate.id().equals(action)) return 0.0;
        for (SkillFilter filter : filters) {
            if (!filter.test(candidate)) return 0.0;
        }
        return flatXp + xpPerUnit * candidate.amount();
    }

    public static XpSource parse(JsonObject json) {
        return new XpSource(
                new ResourceLocation(GsonHelper.getAsString(json, "action")),
                GsonHelper.getAsDouble(json, "xp", 0.0),
                GsonHelper.getAsDouble(json, "xp_per_unit", 0.0),
                SkillFilterTypes.parseList(json, "filters"));
    }
}
