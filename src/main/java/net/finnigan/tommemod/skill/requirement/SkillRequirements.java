package net.finnigan.tommemod.skill.requirement;

import com.google.gson.JsonObject;
import net.finnigan.tommemod.skill.Skill;
import net.finnigan.tommemod.skill.SkillTreeManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The concrete requirement kinds, kept together because they are small and only ever read as a set.
 *
 * Between them they cover every example the design called for:
 * <ul>
 *   <li>"Requires Agility Level 5" - {@code skill_level}</li>
 *   <li>"Requires previous node" / "at Level 2" - {@code node_rank} (implied by a node's parents)</li>
 *   <li>"Requires 3 Agility Points invested" - {@code points_invested}</li>
 *   <li>"Agility >= 10 AND Sprint Mastery >= 3" - {@code and} over two of the above</li>
 * </ul>
 */
public final class SkillRequirements {

    private SkillRequirements() {
    }

    // ---- skill_level ----

    /** {@code {"type":"tommemod:skill_level","level":5}} - optionally {@code "skill"} for another tree. */
    public record SkillLevel(@Nullable ResourceLocation skill, int level) implements SkillRequirement {
        @Override
        public boolean test(SkillContext context) {
            return context.progress().level(context.resolve(skill)) >= level;
        }

        @Override
        public Component describe(SkillContext context) {
            ResourceLocation target = context.resolve(skill);
            return Component.literal(displayName(target) + " Level " + level
                    + " (" + context.progress().level(target) + "/" + level + ")");
        }

        public static SkillRequirement parse(JsonObject json) {
            return new SkillLevel(optionalSkill(json), GsonHelper.getAsInt(json, "level"));
        }
    }

    // ---- node_rank ----

    /** {@code {"type":"tommemod:node_rank","node":"sprint","rank":2}} - the "previous node at Level 2" case. */
    public record NodeRank(@Nullable ResourceLocation skill, String node, int rank) implements SkillRequirement {
        @Override
        public boolean test(SkillContext context) {
            return context.progress().nodeRank(context.resolve(skill), node) >= rank;
        }

        @Override
        public Component describe(SkillContext context) {
            ResourceLocation target = context.resolve(skill);
            int owned = context.progress().nodeRank(target, node);
            String label = nodeTitle(target, node);
            return Component.literal(rank <= 1
                    ? label
                    : label + " rank " + rank + " (" + owned + "/" + rank + ")");
        }

        public static SkillRequirement parse(JsonObject json) {
            return new NodeRank(optionalSkill(json), GsonHelper.getAsString(json, "node"),
                    GsonHelper.getAsInt(json, "rank", 1));
        }
    }

    // ---- points_invested ----

    /** {@code {"type":"tommemod:points_invested","points":3}} - total spent anywhere in a tree. */
    public record PointsInvested(@Nullable ResourceLocation skill, int points) implements SkillRequirement {
        @Override
        public boolean test(SkillContext context) {
            return context.progress().pointsSpent(context.resolve(skill)) >= points;
        }

        @Override
        public Component describe(SkillContext context) {
            ResourceLocation target = context.resolve(skill);
            return Component.literal(points + " points invested in " + displayName(target)
                    + " (" + context.progress().pointsSpent(target) + "/" + points + ")");
        }

        public static SkillRequirement parse(JsonObject json) {
            return new PointsInvested(optionalSkill(json), GsonHelper.getAsInt(json, "points"));
        }
    }

    // ---- all_nodes ----

    /**
     * Every other node in a tree, at some rank. What a capstone is gated on.
     *
     * Counted against the tree as it is loaded rather than against a list written into the file, so a
     * datapack that adds a node to Agility automatically makes Windrunner require that one too - which
     * is the only behaviour that stays correct without someone remembering to edit the capstone.
     *
     * {@code {"type":"tommemod:all_nodes","rank":1}} - raise {@code rank} to demand nodes be fully
     * ranked rather than merely claimed.
     */
    public record AllNodes(@Nullable ResourceLocation skill, int rank) implements SkillRequirement {
        @Override
        public boolean test(SkillContext context) {
            return countRemaining(context) == 0;
        }

        @Override
        public Component describe(SkillContext context) {
            ResourceLocation target = context.resolve(skill);
            int total = countRelevant(context);
            int owned = total - countRemaining(context);
            String what = rank <= 1 ? "claimed" : "at rank " + rank;
            return Component.literal("Every other " + displayName(target) + " node " + what
                    + " (" + owned + "/" + total + ")");
        }

        /** How many of the tree's other nodes are not yet at the required rank. */
        private int countRemaining(SkillContext context) {
            ResourceLocation target = context.resolve(skill);
            Skill tree = SkillTreeManager.skill(target);
            if (tree == null) return 0;

            int remaining = 0;
            for (String otherId : tree.nodes().keySet()) {
                if (isSelf(context, target, otherId)) continue;
                if (context.progress().nodeRank(target, otherId) < rank) remaining++;
            }
            return remaining;
        }

        private int countRelevant(SkillContext context) {
            ResourceLocation target = context.resolve(skill);
            Skill tree = SkillTreeManager.skill(target);
            if (tree == null) return 0;

            int total = 0;
            for (String otherId : tree.nodes().keySet()) {
                if (!isSelf(context, target, otherId)) total++;
            }
            return total;
        }

        /**
         * Only the node carrying this requirement is excluded, and only when it is looking at its own
         * tree - a cross-tree "every Agility node" written on a Gliding capstone means all of them.
         */
        private boolean isSelf(SkillContext context, ResourceLocation target, String otherId) {
            return target.equals(context.skillId()) && otherId.equals(context.nodeId());
        }

        public static SkillRequirement parse(JsonObject json) {
            return new AllNodes(optionalSkill(json), GsonHelper.getAsInt(json, "rank", 1));
        }
    }

    // ---- composites ----

    public record And(List<SkillRequirement> children) implements SkillRequirement {
        @Override
        public boolean test(SkillContext context) {
            return children.stream().allMatch(child -> child.test(context));
        }

        @Override
        public Component describe(SkillContext context) {
            return join(children, context, " and ");
        }

        public static SkillRequirement parse(JsonObject json) {
            return new And(SkillRequirementTypes.parseList(json, "children"));
        }
    }

    public record Or(List<SkillRequirement> children) implements SkillRequirement {
        @Override
        public boolean test(SkillContext context) {
            return children.stream().anyMatch(child -> child.test(context));
        }

        @Override
        public Component describe(SkillContext context) {
            return join(children, context, " or ");
        }

        public static SkillRequirement parse(JsonObject json) {
            return new Or(SkillRequirementTypes.parseList(json, "children"));
        }
    }

    public record Not(SkillRequirement child) implements SkillRequirement {
        @Override
        public boolean test(SkillContext context) {
            return !child.test(context);
        }

        @Override
        public Component describe(SkillContext context) {
            return Component.literal("not ").append(child.describe(context));
        }

        public static SkillRequirement parse(JsonObject json) {
            return new Not(SkillRequirementTypes.parse(GsonHelper.getAsJsonObject(json, "child")));
        }
    }

    // ---- shared parsing and phrasing ----

    @Nullable
    private static ResourceLocation optionalSkill(JsonObject json) {
        return json.has("skill") ? new ResourceLocation(GsonHelper.getAsString(json, "skill")) : null;
    }

    private static Component join(List<SkillRequirement> children, SkillContext context, String separator) {
        var text = Component.empty();
        for (int i = 0; i < children.size(); i++) {
            if (i > 0) text.append(Component.literal(separator));
            text.append(children.get(i).describe(context));
        }
        return text;
    }

    /**
     * A skill's display name, falling back to its id when definitions aren't loaded.
     *
     * Requirements are described on both sides - the client draws them, and the server logs them on a
     * rejected purchase - so this reads through SkillTreeManager's side-aware view rather than
     * assuming either one.
     */
    private static String displayName(ResourceLocation skillId) {
        Skill skill = SkillTreeManager.skill(skillId);
        return skill != null ? skill.displayName() : skillId.getPath();
    }

    private static String nodeTitle(ResourceLocation skillId, String nodeId) {
        Skill skill = SkillTreeManager.skill(skillId);
        if (skill == null) return nodeId;
        var node = skill.node(nodeId);
        return node != null ? node.title() : nodeId;
    }
}
