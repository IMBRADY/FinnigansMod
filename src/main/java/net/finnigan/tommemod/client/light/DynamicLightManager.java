package net.finnigan.tommemod.client.light;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;
import java.util.function.BiFunction;

/**
 * Client-only registry for render-time dynamic lights. Registered items emit while held or dropped,
 * while registered entity types emit from their rendered positions. The terrain shader consumes the
 * nearest sources every frame, so this never rebuilds chunk meshes or changes the world.
 */
public final class DynamicLightManager {
    private static final int WHITE = 0xFFFFFF;
    private static final Map<Item, LightDefinition> ITEM_LIGHTS = new IdentityHashMap<>();
    private static final Map<EntityType<?>, EntityLightDefinition> ENTITY_LIGHTS = new IdentityHashMap<>();

    private DynamicLightManager() {}

    /** Registers an item to emit this level (0-15) both in a player's hand and as a dropped item. */
    public static void registerItem(Item item, int lightLevel) {
        registerItem(item, lightLevel, null, true);
    }

    /** Registers an item with a colour in {@code 0xRRGGBB} format. */
    public static void registerItem(Item item, int lightLevel, int rgb) {
        registerItem(item, lightLevel, rgb, false);
    }

    /** Registers an item with an optional fixed RGB tint and optional vanilla-lightmap mode. */
    public static void registerItem(Item item, int lightLevel, Integer rgb, boolean useVanillaLightmap) {
        registerItem(item, lightLevel, rgb, useVanillaLightmap, true);
    }

    /** Registers an item light and chooses whether fixed RGB retains block/biome hue. */
    public static void registerItem(Item item, int lightLevel, Integer rgb, boolean useVanillaLightmap, boolean preserveMaterialHue) {
        registerItem(item, stack -> lightLevel, rgb, useVanillaLightmap, preserveMaterialHue);
    }

    /** Registers an item with brightness selected from each individual stack. */
    public static void registerItem(Item item, ToIntFunction<ItemStack> lightLevel, Integer rgb,
                                    boolean useVanillaLightmap, boolean preserveMaterialHue) {
        ITEM_LIGHTS.put(item, new LightDefinition(lightLevel,
                rgb == null ? new Vec3(1.0D, 1.0D, 1.0D) : colorFromRgb(rgb),
                useVanillaLightmap, preserveMaterialHue));
    }

    /** Registers all entities of a type to emit a fixed client-side light level. */
    public static void registerEntity(EntityType<?> type, int lightLevel) {
        registerEntity(type, entity -> lightLevel, null, true);
    }

    /** Registers all entities of a type to emit a fixed coloured client-side light. */
    public static void registerEntity(EntityType<?> type, int lightLevel, int rgb) {
        registerEntity(type, entity -> lightLevel, rgb, false);
    }

    /** Registers an entity light with an optional fixed RGB tint and optional vanilla-lightmap mode. */
    public static void registerEntity(EntityType<?> type, int lightLevel, Integer rgb, boolean useVanillaLightmap) {
        registerEntity(type, entity -> lightLevel, rgb, useVanillaLightmap, true);
    }

    /** Registers an entity light and chooses whether fixed RGB retains block/biome hue. */
    public static void registerEntity(EntityType<?> type, int lightLevel, Integer rgb, boolean useVanillaLightmap, boolean preserveMaterialHue) {
        registerEntity(type, entity -> lightLevel, rgb, useVanillaLightmap, preserveMaterialHue);
    }

    /** Registers an entity type with a per-instance light-level function. */
    public static void registerEntity(EntityType<?> type, ToIntFunction<Entity> lightLevel) {
        registerEntity(type, lightLevel, null, true);
    }

    /** Registers an entity type with a per-instance light level and an {@code 0xRRGGBB} colour. */
    public static void registerEntity(EntityType<?> type, ToIntFunction<Entity> lightLevel, int rgb) {
        registerEntity(type, lightLevel, rgb, false);
    }

    /** Registers a variable entity light with an optional fixed RGB tint and optional vanilla-lightmap mode. */
    public static void registerEntity(EntityType<?> type, ToIntFunction<Entity> lightLevel, Integer rgb, boolean useVanillaLightmap) {
        registerEntity(type, lightLevel, rgb, useVanillaLightmap, true);
    }

    /** Registers a variable entity light and chooses whether fixed RGB retains block/biome hue. */
    public static void registerEntity(EntityType<?> type, ToIntFunction<Entity> lightLevel, Integer rgb, boolean useVanillaLightmap, boolean preserveMaterialHue) {
        registerEntitySources(type, lightLevel, rgb, useVanillaLightmap, preserveMaterialHue,
                (entity, partialTick) -> List.of(entity.getPosition(partialTick)));
    }

    /** Registers an entity whose light is emitted from one or more render-interpolated positions. */
    public static void registerEntitySources(EntityType<?> type, ToIntFunction<Entity> lightLevel, Integer rgb,
                                             boolean useVanillaLightmap, boolean preserveMaterialHue,
                                             BiFunction<Entity, Float, List<Vec3>> positions) {
        ENTITY_LIGHTS.put(type, new EntityLightDefinition(lightLevel,
                rgb == null ? new Vec3(1.0D, 1.0D, 1.0D) : colorFromRgb(rgb),
                useVanillaLightmap, preserveMaterialHue, positions));
    }

    /** Collects interpolated source positions for the render thread. */
    public static List<Source> getSources(float partialTick) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return List.of();

        List<Source> sources = new ArrayList<>();
        for (Player player : level.players()) {
            addHeldItem(sources, player, player.getMainHandItem(), partialTick);
            addHeldItem(sources, player, player.getOffhandItem(), partialTick);
        }
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof ItemEntity itemEntity) {
                addSource(sources, entity.getPosition(partialTick), itemEntity.getItem(),
                        ITEM_LIGHTS.get(itemEntity.getItem().getItem()));
            }
            EntityLightDefinition emitter = ENTITY_LIGHTS.get(entity.getType());
            if (emitter != null) {
                int lightLevel = emitter.level().applyAsInt(entity);
                for (Vec3 position : emitter.positions().apply(entity, partialTick)) {
                    addSource(sources, position, lightLevel, emitter.color(), emitter.useVanillaLightmap(), emitter.preserveMaterialHue());
                }
            }
        }
        return sources;
    }

    /** Returns packed-light-compatible block brightness for particles and other non-world shaders. */
    public static int getParticleLightLevel(Vec3 position, float partialTick) {
        int brightest = 0;
        for (Source source : getSources(partialTick)) {
            double distance = Math.sqrt(source.position().distanceToSqr(position));
            brightest = Math.max(brightest, source.level() - (int) Math.floor(distance));
        }
        return clampLight(brightest);
    }

    /** @deprecated The former chunk/entity light-mixin path is no longer registered. */
    @Deprecated(forRemoval = true)
    public static int getLightLevel(BlockPos pos) {
        return 0;
    }

    /** Returns render-time dynamic brightness at an entity or equipment position. */
    public static int getRenderedLightLevel(Vec3 position, float partialTick) {
        return getParticleLightLevel(position, partialTick);
    }

    private static void addHeldItem(List<Source> sources, Player player, ItemStack stack, float partialTick) {
        LightDefinition definition = ITEM_LIGHTS.get(stack.getItem());
        if (definition != null) addSource(sources, player.getEyePosition(partialTick),
                definition.level().applyAsInt(stack), definition.color(),
                definition.useVanillaLightmap(), definition.preserveMaterialHue());
    }

    private static void addSource(List<Source> sources, Vec3 position, ItemStack stack, LightDefinition definition) {
        if (definition != null) addSource(sources, position, definition.level().applyAsInt(stack),
                definition.color(), definition.useVanillaLightmap(), definition.preserveMaterialHue());
    }

    private static void addSource(List<Source> sources, Vec3 position, int lightLevel, Vec3 color) {
        addSource(sources, position, lightLevel, color, false);
    }

    private static void addSource(List<Source> sources, Vec3 position, int lightLevel, Vec3 color, boolean useVanillaLightmap) {
        addSource(sources, position, lightLevel, color, useVanillaLightmap, true);
    }

    private static void addSource(List<Source> sources, Vec3 position, int lightLevel, Vec3 color, boolean useVanillaLightmap, boolean preserveMaterialHue) {
        int clamped = clampLight(lightLevel);
        if (clamped > 0) sources.add(new Source(position, clamped, color, useVanillaLightmap, preserveMaterialHue));
    }

    private static int clampLight(int lightLevel) {
        return Math.max(0, Math.min(15, lightLevel));
    }

    private static Vec3 colorFromRgb(int rgb) {
        return new Vec3((rgb >> 16 & 255) / 255.0D, (rgb >> 8 & 255) / 255.0D, (rgb & 255) / 255.0D);
    }

    public record Source(Vec3 position, int level, Vec3 color, boolean useVanillaLightmap, boolean preserveMaterialHue) {}

    private record LightDefinition(ToIntFunction<ItemStack> level, Vec3 color, boolean useVanillaLightmap, boolean preserveMaterialHue) {}

    private record EntityLightDefinition(ToIntFunction<Entity> level, Vec3 color, boolean useVanillaLightmap,
                                         boolean preserveMaterialHue,
                                         BiFunction<Entity, Float, List<Vec3>> positions) {}

}
