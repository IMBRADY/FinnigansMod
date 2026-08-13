package net.finnigan.tommemod.skill;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.finnigan.tommemod.skill.effect.SkillEffect;
import net.finnigan.tommemod.skill.effect.SkillEffectTypes;
import net.finnigan.tommemod.skill.requirement.SkillRequirement;
import net.finnigan.tommemod.skill.requirement.SkillRequirementTypes;
import net.finnigan.tommemod.skill.requirement.SkillRequirements;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * One purchasable box in a tree.
 *
 * A node is bought a rank at a time up to {@code max_rank}, each rank costing what the {@code cost}
 * list says and adding its share of every effect. Its {@code parents} do two jobs at once: they are
 * what the screen draws lines between, and they are an implicit "rank 1 in a parent" requirement -
 * which is the design's rule that you only need the first level of a node to move past it. Anything
 * stricter than that is written out longhand in {@code requirements} and checked on top.
 *
 * Where a node hangs off several parents, reaching <em>any one</em> of them opens it. That is what
 * makes a fork a real choice: a branch that split into two options and then demanded both back again
 * would only be a longer straight line. Nodes that genuinely want every parent say so with
 * {@code "parent_mode": "all"}.
 */
public record SkillNode(String id, String title, String description, ItemStack icon,
                        int x, int y, int maxRank, List<Integer> costs,
                        List<String> parents, ParentMode parentMode,
                        List<SkillRequirement> requirements, List<SkillEffect> effects) {

    /** Whether a node needs one of its parents or all of them. */
    public enum ParentMode {
        ANY,
        ALL;

        static ParentMode byName(String name) {
            return name.equalsIgnoreCase("all") ? ALL : ANY;
        }
    }

    /** What buying the given rank costs, in this skill's points. */
    public int costOf(int rank) {
        if (costs.isEmpty()) return 1;
        // Ranks past the end of the list keep the last stated price, so raising max_rank later can't
        // hand out free ranks.
        return costs.get(Math.min(Math.max(rank, 1), costs.size()) - 1);
    }

    /** Total points sunk into this node at the given rank - what a refund would be worth. */
    public int totalCostAt(int rank) {
        int total = 0;
        for (int r = 1; r <= rank; r++) {
            total += costOf(r);
        }
        return total;
    }

    /**
     * Every condition on this node, the implicit parent links included.
     *
     * Built here rather than at the call site so nothing downstream has to remember that parents are
     * requirements too - the screen, the server's purchase check and the tooltip all ask one question
     * and get the whole answer.
     */
    public List<SkillRequirement> allRequirements() {
        List<SkillRequirement> all = new ArrayList<>(parents.size() + requirements.size());

        List<SkillRequirement> parentLinks = parents.stream()
                .map(parent -> (SkillRequirement) new SkillRequirements.NodeRank(null, parent, 1))
                .toList();

        if (parentMode == ParentMode.ANY && parentLinks.size() > 1) {
            // One Or rather than several entries, so the detail panel reads "Sprint or Dexterity" on a
            // single line and ticks green as soon as either is owned.
            all.add(new SkillRequirements.Or(parentLinks));
        } else {
            all.addAll(parentLinks);
        }

        all.addAll(requirements);
        return all;
    }

    public static SkillNode parse(JsonObject json) {
        String id = GsonHelper.getAsString(json, "id");

        List<Integer> costs = new ArrayList<>();
        if (json.has("cost")) {
            JsonElement cost = json.get("cost");
            if (cost.isJsonArray()) {
                cost.getAsJsonArray().forEach(entry -> costs.add(entry.getAsInt()));
            } else {
                costs.add(cost.getAsInt());
            }
        } else {
            costs.add(1);
        }

        List<String> parents = new ArrayList<>();
        if (json.has("parents")) {
            GsonHelper.getAsJsonArray(json, "parents").forEach(parent -> parents.add(parent.getAsString()));
        }

        List<SkillEffect> effects = new ArrayList<>();
        if (json.has("effects")) {
            for (JsonElement element : GsonHelper.getAsJsonArray(json, "effects")) {
                effects.add(SkillEffectTypes.parse(GsonHelper.convertToJsonObject(element, "effect")));
            }
        }

        return new SkillNode(id,
                GsonHelper.getAsString(json, "title", id),
                GsonHelper.getAsString(json, "description", ""),
                parseIcon(json, "icon"),
                GsonHelper.getAsInt(json, "x", 0),
                GsonHelper.getAsInt(json, "y", 0),
                GsonHelper.getAsInt(json, "max_rank", 1),
                List.copyOf(costs),
                List.copyOf(parents),
                ParentMode.byName(GsonHelper.getAsString(json, "parent_mode", "any")),
                List.copyOf(SkillRequirementTypes.parseList(json, "requirements")),
                List.copyOf(effects));
    }

    /** An unknown or missing item falls back to a barrier rather than failing the whole tree. */
    static ItemStack parseIcon(JsonObject json, String key) {
        if (!json.has(key)) return new ItemStack(Items.BARRIER);

        ResourceLocation itemId = new ResourceLocation(GsonHelper.getAsString(json, key));
        Item item = ForgeRegistries.ITEMS.getValue(itemId);
        return new ItemStack(item == null ? Items.BARRIER : item);
    }
}
