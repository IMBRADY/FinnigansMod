package net.finnigan.tommemod.village;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * One-shot utility that connects a newly-completed house's door-front position to the nearest
 * existing path block (or, failing that, a simple fallback point) with a straight, Y-snapped-to-
 * -surface line of path blocks. Deliberately not true pathfinding (no A-star, no obstacle
 * avoidance) - mirrors ConstructionSiteBlockEntity's own "auto-flatten within tolerance" precedent
 * for bounded scope.
 * Since paths are small, this places everything immediately rather than queuing/ticking like the
 * house build does.
 */
public class PathBuilder {

    private static final int SEARCH_RADIUS = 24;
    private static final int FALLBACK_DISTANCE = 5;

    private PathBuilder() {
    }

    /** Builds (or extends) a path from the nearest existing path network to just outside the given
     * door-front position. No-op if doorFrontPos is null (i.e. the completed building had no door
     * concept, e.g. a wall segment). */
    public static void buildPathToDoor(ServerLevel level, BlockPos doorFrontPos) {
        if (doorFrontPos == null) return;

        BlockState pathState = level.getBiome(doorFrontPos).is(Biomes.DESERT)
                ? Blocks.SANDSTONE.defaultBlockState()
                : Blocks.DIRT_PATH.defaultBlockState();

        BlockPos source = findExistingPath(level, doorFrontPos);
        if (source == null) {
            source = fallbackSource(doorFrontPos);
        }

        stepLine(level, source, doorFrontPos, pathState);
    }

    /** BFS outward from the door (capped at SEARCH_RADIUS, stepping 2 blocks at a time) looking for
     * an existing dirt-path/sandstone block from a prior path-build, so successive houses connect
     * into one shared network instead of each getting an isolated stub. */
    private static BlockPos findExistingPath(ServerLevel level, BlockPos from) {
        BlockPos start = surfacePos(level, from);
        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        visited.add(start);
        queue.add(start);

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            if (current.distSqr(from) > (double) SEARCH_RADIUS * SEARCH_RADIUS) continue;

            if (!current.equals(start) && isPathBlock(level, current)) {
                return current;
            }

            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos next = surfacePos(level, current.relative(dir, 2));
                if (visited.add(next)) {
                    queue.add(next);
                }
            }
        }
        return null;
    }

    private static boolean isPathBlock(ServerLevel level, BlockPos surfacePos) {
        BlockState state = level.getBlockState(surfacePos);
        return state.is(Blocks.DIRT_PATH) || state.is(Blocks.SANDSTONE);
    }

    /** No existing path network found nearby (e.g. a brand-new village): step a few more blocks
     * further out from the house, in the same direction the door already faces away from it, so a
     * short stub path still gets built rather than nothing at all. */
    private static BlockPos fallbackSource(BlockPos doorFrontPos) {
        return doorFrontPos.offset(0, 0, FALLBACK_DISTANCE);
    }

    private static void stepLine(ServerLevel level, BlockPos from, BlockPos to, BlockState pathState) {
        int dx = to.getX() - from.getX();
        int dz = to.getZ() - from.getZ();
        int steps = Math.max(Math.abs(dx), Math.abs(dz));
        if (steps == 0) {
            placePathBlock(level, to, pathState);
            return;
        }

        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            int x = from.getX() + (int) Math.round(dx * t);
            int z = from.getZ() + (int) Math.round(dz * t);
            placePathBlock(level, new BlockPos(x, from.getY(), z), pathState);
        }
    }

    private static void placePathBlock(ServerLevel level, BlockPos columnPos, BlockState pathState) {
        level.setBlock(surfacePos(level, columnPos), pathState, 3);
    }

    private static BlockPos surfacePos(ServerLevel level, BlockPos columnPos) {
        int y = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, columnPos).getY() - 1;
        return new BlockPos(columnPos.getX(), y, columnPos.getZ());
    }
}
