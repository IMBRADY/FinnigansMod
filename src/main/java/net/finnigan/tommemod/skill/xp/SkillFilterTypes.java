package net.finnigan.tommemod.skill.xp;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.finnigan.tommemod.TommeMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The filter kinds a tree file may name.
 *
 * Everything here resolves tags lazily, at test time rather than at load time: tree files are read
 * during datapack load, and tags are not bound until afterwards, so a filter that looked its tag up
 * eagerly would capture an empty one and silently never match.
 */
public final class SkillFilterTypes {

    private static final Map<ResourceLocation, Function<JsonObject, SkillFilter>> TYPES = new HashMap<>();

    public static final ResourceLocation BLOCK_TAG = register("block_tag", json -> {
        TagKey<Block> tag = TagKey.create(Registries.BLOCK, new ResourceLocation(GsonHelper.getAsString(json, "tag")));
        return action -> action.block() != null && action.block().is(tag);
    });

    public static final ResourceLocation BLOCK = register("block", json -> {
        List<ResourceLocation> ids = idList(json, "block", "blocks");
        return action -> action.block() != null
                && ids.contains(ForgeRegistries.BLOCKS.getKey(action.block().getBlock()));
    });

    public static final ResourceLocation TOOL_TAG = register("tool_tag", json -> {
        TagKey<Item> tag = TagKey.create(Registries.ITEM, new ResourceLocation(GsonHelper.getAsString(json, "tag")));
        return action -> action.tool() != null && action.tool().is(tag);
    });

    public static final ResourceLocation TOOL = register("tool", json -> {
        List<ResourceLocation> ids = idList(json, "item", "items");
        return action -> matchesItem(action.tool(), ids);
    });

    /** Matches an empty hand - the whole basis of the Unarmed tree. */
    public static final ResourceLocation EMPTY_HANDED = register("empty_handed",
            json -> action -> action.tool() == null || action.tool().isEmpty());

    public static final ResourceLocation ITEM_TAG = register("item_tag", json -> {
        TagKey<Item> tag = TagKey.create(Registries.ITEM, new ResourceLocation(GsonHelper.getAsString(json, "tag")));
        return action -> action.item() != null && action.item().is(tag);
    });

    public static final ResourceLocation ITEM = register("item", json -> {
        List<ResourceLocation> ids = idList(json, "item", "items");
        return action -> matchesItem(action.item(), ids);
    });

    public static final ResourceLocation ENTITY_TYPE_TAG = register("entity_type_tag", json -> {
        TagKey<EntityType<?>> tag = TagKey.create(Registries.ENTITY_TYPE,
                new ResourceLocation(GsonHelper.getAsString(json, "tag")));
        return action -> action.entity() != null && action.entity().getType().is(tag);
    });

    public static final ResourceLocation ENTITY_TYPE = register("entity_type", json -> {
        List<ResourceLocation> ids = idList(json, "entity", "entities");
        return action -> action.entity() != null
                && ids.contains(ForgeRegistries.ENTITY_TYPES.getKey(action.entity().getType()));
    });

    /** Gates on how much of the action there was - "only projectile hits past 30 blocks". */
    public static final ResourceLocation MIN_AMOUNT = register("min_amount", json -> {
        double minimum = GsonHelper.getAsDouble(json, "amount");
        return action -> action.amount() >= minimum;
    });

    private SkillFilterTypes() {
    }

    public static ResourceLocation register(String path, Function<JsonObject, SkillFilter> parser) {
        ResourceLocation id = new ResourceLocation(TommeMod.MOD_ID, path);
        TYPES.put(id, parser);
        return id;
    }

    public static SkillFilter parse(JsonObject json) {
        ResourceLocation type = new ResourceLocation(GsonHelper.getAsString(json, "type"));
        Function<JsonObject, SkillFilter> parser = TYPES.get(type);
        if (parser == null) {
            throw new IllegalArgumentException("Unknown skill filter type '" + type + "'");
        }
        return parser.apply(json);
    }

    public static List<SkillFilter> parseList(JsonObject owner, String key) {
        List<SkillFilter> filters = new ArrayList<>();
        if (!owner.has(key)) return filters;

        for (JsonElement element : GsonHelper.getAsJsonArray(owner, key)) {
            filters.add(parse(GsonHelper.convertToJsonObject(element, key)));
        }
        return filters;
    }

    private static boolean matchesItem(@Nullable ItemStack stack, List<ResourceLocation> ids) {
        return stack != null && !stack.isEmpty() && ids.contains(ForgeRegistries.ITEMS.getKey(stack.getItem()));
    }

    /** Accepts either a single id or a list of them, so one-entry filters stay short. */
    private static List<ResourceLocation> idList(JsonObject json, String singularKey, String pluralKey) {
        List<ResourceLocation> ids = new ArrayList<>();
        if (json.has(singularKey)) {
            ids.add(new ResourceLocation(GsonHelper.getAsString(json, singularKey)));
        }
        if (json.has(pluralKey)) {
            for (JsonElement element : GsonHelper.getAsJsonArray(json, pluralKey)) {
                ids.add(new ResourceLocation(element.getAsString()));
            }
        }
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("Filter needs '" + singularKey + "' or '" + pluralKey + "'");
        }
        return ids;
    }

    /** Forces class-init, so the constants above are registered before any tree file is read. */
    public static void bootstrap() {
    }
}
