package net.finnigan.tommemod.village;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

    private static final int HOUSE_SIZE = 5;
    private static final int HOUSE_DOOR_X = HOUSE_SIZE / 2;
    // Shared with wallSegment() so rotation's bounding-box math can't drift out of sync either.
    private static final int WALLS_LENGTH = 9;
    private static final int WALLS_DEPTH = 1;

    private BuildingStructures() {
    }

    public static List<Map.Entry<BlockPos, BlockState>> forType(BuildingType type) {
        return switch (type) {
            case HOUSE -> house();
            case WALLS -> wallSegment();
            default -> List.of();
        };
    }

    /**
     * Same as {@link #forType(BuildingType)} but rotated in-place (around the structure's own
     * footprint, not the world origin) so its door/front faces {@code facing} instead of the
     * default south. Blocks used here (cobblestone, oak planks, cobblestone wall, air) have no
     * facing/connection state that itself needs rotating, so only the X/Z positions are transformed.
     */
    public static List<Map.Entry<BlockPos, BlockState>> forType(BuildingType type, Direction facing) {
        List<Map.Entry<BlockPos, BlockState>> base = forType(type);
        int steps = rotationSteps(facing);
        if (steps == 0 || base.isEmpty()) return base;

        int width = boundsWidth(type);
        int depth = boundsDepth(type);
        List<Map.Entry<BlockPos, BlockState>> rotated = new ArrayList<>(base.size());
        for (Map.Entry<BlockPos, BlockState> e : base) {
            BlockPos p = e.getKey();
            int[] xz = rotateXZ(p.getX(), p.getZ(), width, depth, steps);
            rotated.add(entry(xz[0], p.getY(), xz[1], e.getValue()));
        }
        return rotated;
    }

    /** Maps a facing to a 0-3 count of 90-degree rotation steps, south (this mod's original hardcoded
     * door direction) being the unrotated baseline. Vertical directions aren't meaningful for a
     * horizontal footprint and are treated as unrotated. */
    private static int rotationSteps(Direction facing) {
        return switch (facing) {
            case SOUTH -> 0;
            case WEST -> 1;
            case NORTH -> 2;
            case EAST -> 3;
            default -> 0;
        };
    }

    private static int boundsWidth(BuildingType type) {
        return switch (type) {
            case HOUSE -> HOUSE_SIZE;
            case WALLS -> WALLS_LENGTH;
            default -> 1;
        };
    }

    private static int boundsDepth(BuildingType type) {
        return switch (type) {
            case HOUSE -> HOUSE_SIZE;
            case WALLS -> WALLS_DEPTH;
            default -> 1;
        };
    }

    /** Rotates a point within a width x depth grid by {@code steps} 90-degree turns, keeping all
     * coordinates non-negative (rotates in place around the grid's own footprint, not the origin). */
    private static int[] rotateXZ(int x, int z, int width, int depth, int steps) {
        return switch (steps) {
            case 1 -> new int[]{z, width - 1 - x};
            case 2 -> new int[]{width - 1 - x, depth - 1 - z};
            case 3 -> new int[]{depth - 1 - z, x};
            default -> new int[]{x, z};
        };
    }

    /** 5x5 footprint, cobblestone foundation, 4-tall hollow oak-plank box, cobblestone roof, and a
     * 2-tall door gap centered on the south face. */
    private static List<Map.Entry<BlockPos, BlockState>> house() {
        List<Map.Entry<BlockPos, BlockState>> blocks = new ArrayList<>();
        BlockState planks = Blocks.OAK_PLANKS.defaultBlockState();
        BlockState cobble = Blocks.COBBLESTONE.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();

        int size = HOUSE_SIZE;
        int wallHeight = 4;
        int doorX = HOUSE_DOOR_X;

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

        int length = WALLS_LENGTH;
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
