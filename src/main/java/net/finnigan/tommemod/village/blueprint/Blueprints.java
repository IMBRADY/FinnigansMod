package net.finnigan.tommemod.village.blueprint;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import net.finnigan.tommemod.TommeMod;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModFileInfo;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every building the builders know how to make, in catalog order.
 *
 * <p>Designs are read straight out of the mod jar rather than through a datapack reload listener,
 * on purpose: the client has to draw the ghost of a building, so it needs the same designs the server
 * builds from, and the jar is the one thing both sides are guaranteed to share. Adding a building is
 * adding a JSON file to {@code data/tommemod/blueprints/} and naming it in {@code index.json} there.
 *
 * <p>A file with a mistake in it is skipped and the reason logged ({@code Skipping blueprint ...});
 * the rest still load.
 */
public final class Blueprints {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String ROOT = "data/" + TommeMod.MOD_ID + "/blueprints/";

    @Nullable
    private static volatile List<Blueprint> all;
    private static final Map<String, Blueprint> BY_ID = new HashMap<>();

    private Blueprints() {
    }

    public static List<Blueprint> all() {
        List<Blueprint> loaded = all;
        if (loaded == null) {
            synchronized (Blueprints.class) {
                loaded = all;
                if (loaded == null) {
                    loaded = load();
                    all = loaded;
                }
            }
        }
        return loaded;
    }

    @Nullable
    public static Blueprint get(String id) {
        all();
        return BY_ID.get(id);
    }

    public static int indexOf(String id) {
        List<Blueprint> list = all();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id().equals(id)) return i;
        }
        return -1;
    }

    private static List<Blueprint> load() {
        List<Blueprint> out = new ArrayList<>();
        JsonObject index = readJson("index.json");
        if (index == null) {
            LOGGER.error("No blueprint index at {}index.json - builders have nothing to build", ROOT);
            return Collections.emptyList();
        }
        for (JsonElement e : index.getAsJsonArray("blueprints")) {
            String id = e.getAsString();
            try {
                JsonObject json = readJson(id + ".json");
                if (json == null) throw new IllegalArgumentException("file not found");
                Blueprint bp = parse(id, json);
                out.add(bp);
                BY_ID.put(id, bp);
            } catch (Exception ex) {
                LOGGER.error("Skipping blueprint {}: {}", id, ex.getMessage());
            }
        }
        LOGGER.info("Loaded {} blueprints", out.size());
        return Collections.unmodifiableList(out);
    }

    @Nullable
    private static JsonObject readJson(String file) {
        try (InputStream in = open(file)) {
            if (in == null) return null;
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        } catch (IOException ex) {
            LOGGER.error("Could not read blueprint file {}: {}", file, ex.getMessage());
            return null;
        }
    }

    /** The mod file's own view of its resources first (works in dev and in a built jar alike), with
     * the classloader as a fallback. */
    @Nullable
    private static InputStream open(String file) throws IOException {
        IModFileInfo info = ModList.get() != null ? ModList.get().getModFileById(TommeMod.MOD_ID) : null;
        if (info != null) {
            Path path = info.getFile().findResource((ROOT + file).split("/"));
            if (Files.exists(path)) return Files.newInputStream(path);
        }
        return Blueprints.class.getClassLoader().getResourceAsStream(ROOT + file);
    }

    private static Blueprint parse(String id, JsonObject json) {
        String name = json.get("name").getAsString();
        String description = json.has("description") ? json.get("description").getAsString() : "";
        String category = json.has("category") ? json.get("category").getAsString() : "Buildings";
        String purpose = json.has("purpose") ? json.get("purpose").getAsString() : "";
        String purposeText = json.has("purpose_text") ? json.get("purpose_text").getAsString() : "";
        Item icon = json.has("icon") ? item(json.get("icon").getAsString()) : Items.BRICKS;
        int builders = json.has("builders") ? json.get("builders").getAsInt() : 1;
        BlockState foundation = json.has("foundation")
                ? state(json.get("foundation").getAsString())
                : Blocks.DIRT.defaultBlockState();

        List<Blueprint.Cost> cost = new ArrayList<>();
        if (json.has("cost")) {
            for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("cost").entrySet()) {
                Item item = item(e.getKey());
                int count = e.getValue().getAsInt();
                if (count > 0) cost.add(new Blueprint.Cost(item, count));
            }
        }

        Map<Character, BlockState> palette = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("palette").entrySet()) {
            if (e.getKey().length() != 1) throw new IllegalArgumentException("palette key '" + e.getKey() + "' must be one character");
            char c = e.getKey().charAt(0);
            if (c == '.' || c == ' ' || c == '~') throw new IllegalArgumentException("palette key '" + c + "' is reserved");
            palette.put(c, state(e.getValue().getAsString()));
        }

        JsonArray layers = json.getAsJsonArray("layers");
        int height = layers.size();
        if (height == 0) throw new IllegalArgumentException("no layers");
        int depth = -1;
        int width = -1;
        List<Blueprint.Cell> cells = new ArrayList<>();

        for (int y = 0; y < height; y++) {
            JsonArray rows = layers.get(y).getAsJsonArray();
            if (depth < 0) depth = rows.size();
            if (rows.size() != depth) throw new IllegalArgumentException("layer " + y + " has " + rows.size() + " rows, expected " + depth);
            for (int z = 0; z < depth; z++) {
                String row = rows.get(z).getAsString();
                if (width < 0) width = row.length();
                if (row.length() != width) {
                    throw new IllegalArgumentException("layer " + y + " row " + z + " is " + row.length() + " wide, expected " + width);
                }
                for (int x = 0; x < width; x++) {
                    char c = row.charAt(x);
                    if (c == '~') continue;
                    if (c == '.' || c == ' ') {
                        // Ground level is left alone; above it, the volume is cleared.
                        if (y > 0) cells.add(new Blueprint.Cell(new BlockPos(x, y, z), Blueprint.air()));
                        continue;
                    }
                    BlockState s = palette.get(c);
                    if (s == null) throw new IllegalArgumentException("layer " + y + " row " + z + " uses '" + c + "', which is not in the palette");
                    cells.add(new Blueprint.Cell(new BlockPos(x, y, z), s));
                }
            }
        }

        List<Blueprint.Port> ports = new ArrayList<>();
        if (json.has("ports")) {
            for (JsonElement e : json.getAsJsonArray("ports")) {
                JsonObject p = e.getAsJsonObject();
                Direction facing = Direction.byName(p.get("facing").getAsString());
                Direction outside = Direction.byName(p.get("outside").getAsString());
                if (facing == null || outside == null || facing.getAxis().isVertical() || outside.getAxis().isVertical()) {
                    throw new IllegalArgumentException("port directions must be north, south, east or west");
                }
                ports.add(new Blueprint.Port(new BlockPos(p.get("x").getAsInt(), 0, p.get("z").getAsInt()), facing, outside));
            }
        }

        return new Blueprint(id, name, description, category, purpose, purposeText, icon, builders, foundation, cost,
                width, height, depth, cells, ports, json.has("runs") && json.get("runs").getAsBoolean(),
                json.has("walkway") ? json.get("walkway").getAsInt() : -1);
    }

    private static BlockState state(String text) {
        try {
            return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), text, false).blockState();
        } catch (CommandSyntaxException ex) {
            throw new IllegalArgumentException("bad block state '" + text + "': " + ex.getMessage());
        }
    }

    private static Item item(String text) {
        ResourceLocation id = ResourceLocation.tryParse(text);
        Item item = id != null ? ForgeRegistries.ITEMS.getValue(id) : null;
        if (item == null || item == Items.AIR) throw new IllegalArgumentException("unknown item '" + text + "'");
        return item;
    }
}
