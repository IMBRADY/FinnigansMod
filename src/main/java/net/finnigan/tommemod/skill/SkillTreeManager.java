package net.finnigan.tommemod.skill;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import net.finnigan.tommemod.skill.curve.XpCurveTypes;
import net.finnigan.tommemod.skill.effect.AttributeSkillEffect;
import net.finnigan.tommemod.skill.effect.SkillEffect;
import net.finnigan.tommemod.skill.effect.SkillEffectTypes;
import net.finnigan.tommemod.skill.requirement.SkillRequirementTypes;
import net.finnigan.tommemod.skill.xp.SkillFilterTypes;
import net.finnigan.tommemod.skill.xp.XpSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.ai.attributes.Attribute;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Where the loaded trees live, and the only place anything asks about them.
 *
 * There is deliberately one copy of the definitions rather than a server set and a client set. On a
 * dedicated server the datapack listener fills it; on a client connected to one, the sync packet
 * does; in single-player both do, with identical content. That keeps a node's requirements evaluating
 * to the same answer in the screen the player is looking at and in the server check that actually
 * grants it, which is the one property this whole design depends on.
 *
 * Two indices are built at load time because they are read on hot paths: {@link #sourcesFor}, hit
 * every time a player moves or swings, and {@link #touchedAttributes}, walked whenever a purchase
 * forces a recompute.
 */
public final class SkillTreeManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();

    public static final String TREE_DIRECTORY = "skill_trees";
    public static final String CATEGORY_DIRECTORY = "skill_categories";

    private static Map<ResourceLocation, Skill> skills = Map.of();
    private static Map<ResourceLocation, SkillCategory> categories = Map.of();
    private static List<Skill> orderedSkills = List.of();
    private static List<SkillCategory> orderedCategories = List.of();
    private static Map<ResourceLocation, List<SourceBinding>> actionIndex = Map.of();
    private static Set<Attribute> touchedAttributes = Set.of();

    /** The JSON exactly as it was read, kept so the definitions can be resent to clients verbatim. */
    private static Map<ResourceLocation, String> rawSkills = Map.of();
    private static Map<ResourceLocation, String> rawCategories = Map.of();

    /** An XP source together with the skill it pays into - what the action index stores. */
    public record SourceBinding(ResourceLocation skillId, XpSource source) {
    }

    private SkillTreeManager() {
    }

    // ---- Reading ----

    @Nullable
    public static Skill skill(ResourceLocation id) {
        return skills.get(id);
    }

    public static Collection<Skill> skills() {
        return skills.values();
    }

    /** Every skill, in the order the sidebar should list them: by category, then by sort_order. */
    public static List<Skill> orderedSkills() {
        return orderedSkills;
    }

    public static List<SkillCategory> orderedCategories() {
        return orderedCategories;
    }

    @Nullable
    public static SkillCategory category(ResourceLocation id) {
        return categories.get(id);
    }

    /** The category a skill sits under, or null if it names one that was not loaded. */
    @Nullable
    public static SkillCategory categoryOf(ResourceLocation skillId) {
        Skill skill = skills.get(skillId);
        return skill == null ? null : categories.get(skill.category());
    }

    /** Whether this skill sits under a heading where picking one closes off the rest. */
    public static boolean isExclusive(ResourceLocation skillId) {
        SkillCategory category = categoryOf(skillId);
        return category != null && category.exclusive();
    }

    /**
     * Every skill that competes with this one for the same commitment, itself included.
     *
     * Empty for anything outside an exclusive category, which is the ordinary case and the reason
     * callers can treat an empty answer as "no commitment involved" rather than as an error.
     */
    public static List<Skill> exclusiveGroupOf(ResourceLocation skillId) {
        SkillCategory category = categoryOf(skillId);
        if (category == null || !category.exclusive()) return List.of();

        List<Skill> group = new ArrayList<>();
        for (Skill skill : orderedSkills) {
            if (skill.category().equals(category.id())) group.add(skill);
        }
        return group;
    }

    /** Everything that earns experience from a given action. Empty is the common case; don't allocate. */
    public static List<SourceBinding> sourcesFor(ResourceLocation actionId) {
        return actionIndex.getOrDefault(actionId, List.of());
    }

    /**
     * Every attribute any node in any tree touches.
     *
     * Recomputing a player's modifiers means clearing the old ones first, and this is the set that
     * has to be cleared - walking the whole attribute registry instead would be wasteful, and
     * remembering which attributes a player was previously granted would be one more piece of state
     * to get out of step with the definitions after a datapack reload.
     */
    public static Set<Attribute> touchedAttributes() {
        return touchedAttributes;
    }

    public static boolean isLoaded() {
        return !skills.isEmpty();
    }

    public static Map<ResourceLocation, String> rawSkills() {
        return rawSkills;
    }

    public static Map<ResourceLocation, String> rawCategories() {
        return rawCategories;
    }

    // ---- Loading ----

    /**
     * Replaces the loaded definitions wholesale.
     *
     * Takes raw JSON rather than parsed objects so that both entry points - the datapack listener and
     * the sync packet - go through exactly the same parser. A tree that loads on the server and fails
     * to parse on the client would be far worse than one that fails on both.
     */
    public static void install(Map<ResourceLocation, String> categoryJson, Map<ResourceLocation, String> skillJson) {
        bootstrapTypes();

        Map<ResourceLocation, SkillCategory> parsedCategories = new HashMap<>();
        categoryJson.forEach((id, json) -> {
            try {
                parsedCategories.put(id, SkillCategory.parse(id, GsonHelper.parse(json)));
            } catch (Exception e) {
                LOGGER.error("Skipping skill category {}: {}", id, e.getMessage());
            }
        });

        Map<ResourceLocation, Skill> parsedSkills = new HashMap<>();
        Map<ResourceLocation, String> keptSkillJson = new HashMap<>();
        skillJson.forEach((id, json) -> {
            try {
                parsedSkills.put(id, Skill.parse(id, GsonHelper.parse(json)));
                keptSkillJson.put(id, json);
            } catch (Exception e) {
                // One bad tree file loses that tree, not the whole system. The rest stay playable and
                // the log names the offender.
                LOGGER.error("Skipping skill tree {}: {}", id, e.getMessage());
            }
        });

        categories = Map.copyOf(parsedCategories);
        skills = Map.copyOf(parsedSkills);
        rawCategories = Map.copyOf(categoryJson);
        rawSkills = Map.copyOf(keptSkillJson);
        rebuildIndices();

        LOGGER.info("Loaded {} skill trees in {} categories", skills.size(), categories.size());
    }

    private static void rebuildIndices() {
        List<SkillCategory> categoryOrder = new ArrayList<>(categories.values());
        categoryOrder.sort(Comparator.comparingInt(SkillCategory::sortOrder)
                .thenComparing(category -> category.id().toString()));
        orderedCategories = List.copyOf(categoryOrder);

        Map<ResourceLocation, Integer> categoryRank = new HashMap<>();
        for (int i = 0; i < orderedCategories.size(); i++) {
            categoryRank.put(orderedCategories.get(i).id(), i);
        }

        List<Skill> skillOrder = new ArrayList<>(skills.values());
        // Skills under an unknown category sort to the end rather than crashing the sort - the
        // sidebar shows them under their raw id, which is a visible, fixable problem.
        skillOrder.sort(Comparator
                .comparingInt((Skill skill) -> categoryRank.getOrDefault(skill.category(), Integer.MAX_VALUE))
                .thenComparingInt(Skill::sortOrder)
                .thenComparing(skill -> skill.id().toString()));
        orderedSkills = List.copyOf(skillOrder);

        Map<ResourceLocation, List<SourceBinding>> index = new LinkedHashMap<>();
        for (Skill skill : skills.values()) {
            for (XpSource source : skill.xpSources()) {
                index.computeIfAbsent(source.action(), key -> new ArrayList<>())
                        .add(new SourceBinding(skill.id(), source));
            }
        }
        index.replaceAll((action, bindings) -> List.copyOf(bindings));
        actionIndex = Map.copyOf(index);

        Set<Attribute> attributes = new HashSet<>();
        for (Skill skill : skills.values()) {
            for (SkillNode node : skill.nodes().values()) {
                for (SkillEffect effect : node.effects()) {
                    if (effect instanceof AttributeSkillEffect attributeEffect) {
                        attributes.add(attributeEffect.attribute());
                    }
                }
            }
        }
        touchedAttributes = Set.copyOf(attributes);
    }

    /** Every type registry has to be populated before the first file is parsed. */
    private static void bootstrapTypes() {
        SkillEffectTypes.bootstrap();
        SkillRequirementTypes.bootstrap();
        SkillFilterTypes.bootstrap();
        XpCurveTypes.bootstrap();
    }

    // ---- Datapack plumbing ----

    /**
     * Reads one of the two directories and reinstalls everything.
     *
     * Two of these are registered, one per directory, and each stashes its own half before calling
     * install with both. Installing per-directory instead would sort the sidebar against categories
     * that hadn't been read yet on whichever listener happened to run first.
     */
    public static class ReloadListener extends SimpleJsonResourceReloadListener {

        private static final Map<ResourceLocation, String> STAGED_CATEGORIES = new HashMap<>();
        private static final Map<ResourceLocation, String> STAGED_SKILLS = new HashMap<>();

        private final boolean isCategories;

        public ReloadListener(String directory) {
            super(GSON, directory);
            this.isCategories = directory.equals(CATEGORY_DIRECTORY);
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager resourceManager,
                             ProfilerFiller profiler) {
            Map<ResourceLocation, String> staged = isCategories ? STAGED_CATEGORIES : STAGED_SKILLS;
            staged.clear();
            objects.forEach((id, element) -> staged.put(id, element.toString()));

            install(Map.copyOf(STAGED_CATEGORIES), Map.copyOf(STAGED_SKILLS));
        }
    }
}
