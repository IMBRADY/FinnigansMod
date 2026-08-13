package net.finnigan.tommemod.skill;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.finnigan.tommemod.skill.curve.XpCurve;
import net.finnigan.tommemod.skill.curve.XpCurveTypes;
import net.finnigan.tommemod.skill.xp.XpSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One skill: its own experience, its own levels, its own points, and its own tree to spend them in.
 *
 * The separation the design turns on lives here. Experience is earned by doing the thing ({@link
 * #xpSources}) and buys levels through {@link #curve}; levels hand out points at {@link
 * #pointsPerLevel}; points are only ever spendable on this skill's own {@link #nodes}. There is no
 * pool of generic experience anywhere in the system, and no way for Mining levels to buy an Agility
 * node.
 */
public record Skill(ResourceLocation id, ResourceLocation category, String displayName,
                    String description, ItemStack icon, int sortOrder,
                    int maxLevel, int pointsPerLevel, XpCurve curve,
                    List<XpSource> xpSources, Map<String, SkillNode> nodes) {

    @Nullable
    public SkillNode node(String nodeId) {
        return nodes.get(nodeId);
    }

    /** Experience needed to leave the given level. Zero once the cap is reached. */
    public double xpToAdvance(int level) {
        return level >= maxLevel ? 0.0 : curve.xpToAdvance(level);
    }

    public static Skill parse(ResourceLocation id, JsonObject json) {
        Map<String, SkillNode> nodes = new LinkedHashMap<>();
        for (JsonElement element : GsonHelper.getAsJsonArray(json, "nodes")) {
            SkillNode node = SkillNode.parse(GsonHelper.convertToJsonObject(element, "node"));
            if (nodes.put(node.id(), node) != null) {
                throw new IllegalArgumentException("Duplicate node id '" + node.id() + "' in skill " + id);
            }
        }
        validateParents(id, nodes);

        List<XpSource> sources = new ArrayList<>();
        if (json.has("xp_sources")) {
            for (JsonElement element : GsonHelper.getAsJsonArray(json, "xp_sources")) {
                sources.add(XpSource.parse(GsonHelper.convertToJsonObject(element, "xp_source")));
            }
        }

        XpCurve curve = json.has("xp_curve")
                ? XpCurveTypes.parse(GsonHelper.getAsJsonObject(json, "xp_curve"))
                : XpCurveTypes.DEFAULT;

        return new Skill(id,
                new ResourceLocation(GsonHelper.getAsString(json, "category")),
                GsonHelper.getAsString(json, "display_name", id.getPath()),
                GsonHelper.getAsString(json, "description", ""),
                SkillNode.parseIcon(json, "icon"),
                GsonHelper.getAsInt(json, "sort_order", 0),
                GsonHelper.getAsInt(json, "max_level", 100),
                GsonHelper.getAsInt(json, "points_per_level", 1),
                curve,
                List.copyOf(sources),
                Map.copyOf(nodes));
    }

    /**
     * Catches a parent naming a node that isn't there.
     *
     * Worth failing the file over: a typo'd parent would otherwise produce a node whose implicit
     * "rank 1 in <missing>" requirement can never be satisfied, which in play looks like a node that
     * is simply, silently, permanently locked.
     */
    private static void validateParents(ResourceLocation id, Map<String, SkillNode> nodes) {
        for (SkillNode node : nodes.values()) {
            for (String parent : node.parents()) {
                if (!nodes.containsKey(parent)) {
                    throw new IllegalArgumentException(
                            "Node '" + node.id() + "' in skill " + id + " names unknown parent '" + parent + "'");
                }
            }
        }
    }
}
