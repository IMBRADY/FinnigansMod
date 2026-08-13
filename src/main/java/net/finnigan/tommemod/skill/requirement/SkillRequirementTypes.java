package net.finnigan.tommemod.skill.requirement;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.finnigan.tommemod.TommeMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** The requirement kinds a tree file may name. */
public final class SkillRequirementTypes {

    private static final Map<ResourceLocation, Function<JsonObject, SkillRequirement>> TYPES = new HashMap<>();

    public static final ResourceLocation SKILL_LEVEL = register("skill_level", SkillRequirements.SkillLevel::parse);
    public static final ResourceLocation NODE_RANK = register("node_rank", SkillRequirements.NodeRank::parse);
    public static final ResourceLocation POINTS_INVESTED =
            register("points_invested", SkillRequirements.PointsInvested::parse);
    public static final ResourceLocation ALL_NODES = register("all_nodes", SkillRequirements.AllNodes::parse);
    public static final ResourceLocation AND = register("and", SkillRequirements.And::parse);
    public static final ResourceLocation OR = register("or", SkillRequirements.Or::parse);
    public static final ResourceLocation NOT = register("not", SkillRequirements.Not::parse);

    private SkillRequirementTypes() {
    }

    public static ResourceLocation register(String path, Function<JsonObject, SkillRequirement> parser) {
        ResourceLocation id = new ResourceLocation(TommeMod.MOD_ID, path);
        TYPES.put(id, parser);
        return id;
    }

    public static SkillRequirement parse(JsonObject json) {
        ResourceLocation type = new ResourceLocation(GsonHelper.getAsString(json, "type"));
        Function<JsonObject, SkillRequirement> parser = TYPES.get(type);
        if (parser == null) {
            throw new IllegalArgumentException("Unknown skill requirement type '" + type + "'");
        }
        return parser.apply(json);
    }

    public static List<SkillRequirement> parseList(JsonObject owner, String key) {
        List<SkillRequirement> requirements = new ArrayList<>();
        if (!owner.has(key)) return requirements;

        for (JsonElement element : GsonHelper.getAsJsonArray(owner, key)) {
            requirements.add(parse(GsonHelper.convertToJsonObject(element, key)));
        }
        return requirements;
    }

    /** Forces class-init, so the constants above are registered before any tree file is read. */
    public static void bootstrap() {
    }
}
