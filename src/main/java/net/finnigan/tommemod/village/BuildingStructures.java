package net.finnigan.tommemod.village;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Procedural placeholder structures for the implemented BuildingTypes - simple programmer-art box
 * shapes using vanilla blocks, since no real building designs/schematics exist yet. Each returned
 * entry is a position offset (relative to wherever the construction banner is placed) plus the
 * BlockState that should end up there.
 */
public class BuildingStructures {

    private BuildingStructures() {
    }

    public static List<Map.Entry<BlockPos, BlockState>> forType(BuildingType type) {
        return switch (type) {
            case HOUSE -> house();
            case WALLS -> wallSegment();
            default -> List.of();
        };
    }

    /** 5x5 footprint, cobblestone foundation, 4-tall hollow oak-plank box, cobblestone roof, and a
     * 2-tall door gap centered on the south face. */
    private static List<Map.Entry<BlockPos, BlockState>> house() {
        List<Map.Entry<BlockPos, BlockState>> blocks = new ArrayList<>();
        BlockState planks = Blocks.OAK_PLANKS.defaultBlockState();
        BlockState cobble = Blocks.COBBLESTONE.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();

        int size = 5;
        int wallHeight = 4;
        int doorX = size / 2;

        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                blocks.add(entry(x, 0, z, cobble));
            }
        }

        for (int y = 1; y <= wallHeight; y++) {
            for (int x = 0; x < size; x++) {
                for (int z = 0; z < size; z++) {
                    boolean edge = x == 0 || x == size - 1 || z == 0 || z == size - 1;
                    if (!edge) continue;

                    if (z == size - 1 && x == doorX && y <= 2) {
                        blocks.add(entry(x, y, z, air));
                        continue;
                    }
                    blocks.add(entry(x, y, z, planks));
                }
            }
        }

        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                blocks.add(entry(x, wallHeight + 1, z, cobble));
            }
        }

        return blocks;
    }

    /** A fixed 9-block-long, 3-tall cobblestone wall segment with alternating cobblestone-wall
     * crenellations along the top - a placeable fortification piece, not a village-perimeter
     * tracing structure (players place several where they want walls, same as placing houses). */
    private static List<Map.Entry<BlockPos, BlockState>> wallSegment() {
        List<Map.Entry<BlockPos, BlockState>> blocks = new ArrayList<>();
        BlockState cobble = Blocks.COBBLESTONE.defaultBlockState();
        BlockState wall = Blocks.COBBLESTONE_WALL.defaultBlockState();

        int length = 9;
        int solidHeight = 3;

        for (int x = 0; x < length; x++) {
            for (int y = 0; y < solidHeight; y++) {
                blocks.add(entry(x, y, 0, cobble));
            }
            if (x % 2 == 0) {
                blocks.add(entry(x, solidHeight, 0, wall));
            }
        }

        return blocks;
    }

    private static Map.Entry<BlockPos, BlockState> entry(int x, int y, int z, BlockState state) {
        return new AbstractMap.SimpleEntry<>(new BlockPos(x, y, z), state);
    }
}
