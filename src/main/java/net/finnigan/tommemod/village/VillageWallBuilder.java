package net.finnigan.tommemod.village;

import net.finnigan.tommemod.config.ModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Traces a village's perimeter and raises its walls along it.
 *
 * <p>The perimeter is not invented here. A village's footprint is the union of its claimed POIs'
 * coverage discs (see {@link VillageManager#resolveVillage}), and the Chief Desk's map tab already
 * draws the boundary of exactly that union - so the wall is built on the outline the player was
 * looking at when they decided to buy it. Freezing that outline into a {@link VillageWalls} at the
 * same moment is what stops the wall and the village's idea of itself from ever disagreeing.
 *
 * <p>The wall itself is a placeholder: a column of oak logs per perimeter block, built from the
 * ground up. Swapping in a real design means changing {@link #raise} and nothing else - every other
 * part of the mod asks {@link VillageWalls} where the boundary is, not what it is made of.
 */
public final class VillageWallBuilder {

    private VillageWallBuilder() {
    }

    /**
     * Why this village cannot be walled right now, phrased for the player, or null if it can be.
     * Separate from {@link #raise} so the Chief Desk can refuse a purchase before charging for it.
     */
    @Nullable
    public static String blockerFor(ServerLevel level, VillageManager manager, UUID villageId) {
        if (manager.hasWalls(villageId)) return "This village is already walled";

        List<BlockPos> pois = manager.getPoiPositions(villageId);
        if (pois.isEmpty()) return "No claimed beds or job sites to build a perimeter around";

        int columns = ringFor(manager, villageId).perimeterColumns().size();
        if (columns == 0) return "This village has no perimeter to build on";

        int cap = ModConfig.VILLAGE_WALLS_MAX_COLUMNS.get();
        if (columns > cap) {
            return "Perimeter is too long to wall (" + columns + " blocks, limit " + cap + ")";
        }
        return null;
    }

    /**
     * Raises the walls and hands the ring to {@link VillageManager}, which from then on treats it as
     * the village's boundary. The ring is recorded first: a wall that the world has but the manager
     * does not is a wall nothing in the mod respects.
     */
    public static void raise(ServerLevel level, VillageManager manager, UUID villageId) {
        VillageWalls walls = ringFor(manager, villageId);
        List<BlockPos> perimeter = walls.perimeterColumns();
        if (perimeter.isEmpty()) return;

        manager.setWalls(villageId, walls);

        BlockState log = Blocks.OAK_LOG.defaultBlockState();
        int height = ModConfig.VILLAGE_WALLS_HEIGHT.get();
        int cap = ModConfig.VILLAGE_WALLS_MAX_COLUMNS.get();
        int built = 0;

        for (BlockPos column : perimeter) {
            if (built++ >= cap) break;
            if (!level.hasChunk(column.getX() >> 4, column.getZ() >> 4)) continue;

            int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
            for (int y = 0; y < height; y++) {
                BlockPos pos = new BlockPos(column.getX(), groundY + y, column.getZ());
                if (!level.isInWorldBounds(pos)) break;
                // UPDATE_CLIENTS only: a perimeter is thousands of blocks, and letting each one kick
                // off neighbour updates turns raising a wall into a tick the server does not come
                // back from. Logs neither need nor react to a neighbour update anyway.
                level.setBlock(pos, log, Block.UPDATE_CLIENTS);
            }
        }
    }

    /**
     * The ring this village would be walled along: one disc per claimed POI, at the radius each POI
     * already projects. Same shape, same radius, same source of truth as the map outline.
     */
    private static VillageWalls ringFor(VillageManager manager, UUID villageId) {
        return new VillageWalls(manager.getPoiPositions(villageId), ModConfig.POI_LINK_RADIUS.get());
    }
}
