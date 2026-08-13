package net.finnigan.tommemod.skill.effect;

import com.google.gson.JsonObject;
import net.finnigan.tommemod.TommeMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/** The effect kinds a tree file may name. Open for extension, closed to node-specific code. */
public final class SkillEffectTypes {

    private static final Map<ResourceLocation, Function<JsonObject, SkillEffect>> TYPES = new HashMap<>();

    public static final ResourceLocation ATTRIBUTE = register("attribute", AttributeSkillEffect::parse);
    public static final ResourceLocation BONUS = register("bonus", BonusSkillEffect::parse);

    private SkillEffectTypes() {
    }

    public static ResourceLocation register(String path, Function<JsonObject, SkillEffect> parser) {
        ResourceLocation id = new ResourceLocation(TommeMod.MOD_ID, path);
        TYPES.put(id, parser);
        return id;
    }

    public static SkillEffect parse(JsonObject json) {
        ResourceLocation type = new ResourceLocation(GsonHelper.getAsString(json, "type"));
        Function<JsonObject, SkillEffect> parser = TYPES.get(type);
        if (parser == null) {
            throw new IllegalArgumentException("Unknown skill effect type '" + type + "'");
        }
        return parser.apply(json);
    }

    /** Forces class-init, so the constants above are registered before any tree file is read. */
    public static void bootstrap() {
    }
}
