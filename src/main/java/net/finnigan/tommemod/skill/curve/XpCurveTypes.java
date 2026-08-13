package net.finnigan.tommemod.skill.curve;

import com.google.gson.JsonObject;
import net.finnigan.tommemod.TommeMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/** The curve shapes a tree file may name in its {@code xp_curve} block. */
public final class XpCurveTypes {

    private static final Map<ResourceLocation, Function<JsonObject, XpCurve>> TYPES = new HashMap<>();

    /** {@code base * level^exponent} - the default shape, and the one every stock tree uses. */
    public static final ResourceLocation POLYNOMIAL = register("polynomial", json -> {
        double base = GsonHelper.getAsDouble(json, "base", 100.0);
        double exponent = GsonHelper.getAsDouble(json, "exponent", 1.5);
        return level -> base * Math.pow(level, exponent);
    });

    /** {@code base + step * (level - 1)} - a flatter grind, for skills with rarer actions. */
    public static final ResourceLocation LINEAR = register("linear", json -> {
        double base = GsonHelper.getAsDouble(json, "base", 100.0);
        double step = GsonHelper.getAsDouble(json, "step", 50.0);
        return level -> base + step * (level - 1);
    });

    /** An explicit cost per level; past the end of the list the last entry repeats. */
    public static final ResourceLocation TABLE = register("table", json -> {
        var array = GsonHelper.getAsJsonArray(json, "levels");
        double[] costs = new double[array.size()];
        for (int i = 0; i < array.size(); i++) {
            costs[i] = array.get(i).getAsDouble();
        }
        if (costs.length == 0) throw new IllegalArgumentException("table xp_curve needs at least one level");
        return level -> costs[Math.min(Math.max(level, 1), costs.length) - 1];
    });

    /** What a skill gets if it declares no curve at all. */
    public static final XpCurve DEFAULT = level -> 100.0 * Math.pow(level, 1.5);

    private XpCurveTypes() {
    }

    public static ResourceLocation register(String path, Function<JsonObject, XpCurve> parser) {
        ResourceLocation id = new ResourceLocation(TommeMod.MOD_ID, path);
        TYPES.put(id, parser);
        return id;
    }

    public static XpCurve parse(JsonObject json) {
        ResourceLocation type = new ResourceLocation(
                GsonHelper.getAsString(json, "type", POLYNOMIAL.toString()));
        Function<JsonObject, XpCurve> parser = TYPES.get(type);
        if (parser == null) {
            throw new IllegalArgumentException("Unknown xp curve type '" + type + "'");
        }
        return parser.apply(json);
    }

    /** Forces class-init, so the constants above are registered before any tree file is read. */
    public static void bootstrap() {
    }
}
