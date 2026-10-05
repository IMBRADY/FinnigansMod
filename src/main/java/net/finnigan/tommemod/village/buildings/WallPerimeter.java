package net.finnigan.tommemod.village.buildings;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.village.VillageManager;
import net.finnigan.tommemod.village.VillageWalls;
import net.finnigan.tommemod.village.blueprint.Blueprint;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Turns a village's wall pieces into its boundary - once, and only once, they close a loop.
 *
 * <p>The test is a flood fill. Every column a standing wall piece (wall, corner or gatehouse) covers
 * is a barrier; the fill starts outside the lot and runs everywhere it can reach. Ground it cannot
 * reach is enclosed. No enclosed ground means the loop has a gap somewhere, and the village keeps
 * its old POI-based outline; some, and that ground plus the walls around it becomes the village
 * from then on (see {@link VillageWalls}). A gatehouse counts as wall - the gate is the way in, not
 * a hole in the ring.
 *
 * <p>Rechecked whenever a wall piece is finished or knocked down, and every few seconds while any
 * village has pieces standing, so a wall broken by a creeper stops counting once it no longer
 * stands (by the usual {@link VillageBuildings#isStanding} rule).
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public final class WallPerimeter {

    private static final int CHECK_INTERVAL = 200;
    /** Room round the pieces for the fill to get all the way round them. */
    private static final int MARGIN = 2;
    /** Bounding boxes past this many columns are refused - a mistake, not a village. */
    private static final int MAX_COLUMNS = 2048 * 2048;
    /** How far into the wall from enclosed ground still counts as the wall (gatehouses are 6 deep). */
    private static final int WALL_DEPTH = 10;

    /** Which standing pieces each village's walls were last worked out from. */
    private static final Map<UUID, List<UUID>> LAST_PIECES = new HashMap<>();

    private WallPerimeter() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        if (server == null || server.getTickCount() % CHECK_INTERVAL != 0) return;

        for (ServerLevel level : server.getAllLevels()) {
            Map<UUID, List<UUID>> standing = new HashMap<>();
            VillageBuildings data = VillageBuildings.get(level);
            for (VillageBuildings.Building b : data.all()) {
                if (!BuildingPurpose.WALL.equals(b.purpose)) continue;
                List<UUID> ids = standing.computeIfAbsent(b.villageId, k -> new ArrayList<>());
                if (data.isStanding(level, b)) ids.add(b.id);
            }
            standing.forEach((village, ids) -> {
                if (!ids.equals(LAST_PIECES.get(village))) recompute(level, village);
            });
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LAST_PIECES.clear();
    }

    /** Works the village's boundary out again from its standing wall pieces. */
    public static void recompute(ServerLevel level, UUID villageId) {
        VillageBuildings data = VillageBuildings.get(level);
        VillageManager manager = VillageManager.get(level);

        List<VillageBuildings.Building> pieces = new ArrayList<>();
        List<UUID> ids = new ArrayList<>();
        for (VillageBuildings.Building b : data.inVillage(level, villageId)) {
            if (BuildingPurpose.WALL.equals(b.purpose) && data.isStanding(level, b)) {
                pieces.add(b);
                ids.add(b.id);
            }
        }
        LAST_PIECES.put(villageId, ids);

        VillageWalls walls = enclose(pieces);
        if (walls != null) {
            manager.setWalls(villageId, walls);
            return;
        }
        // The loop is open. A ring from an old save (the Chief Desk's log wall) has no pieces to lose,
        // so it stays; one that came from these walls goes with them.
        VillageWalls current = manager.getWalls(villageId);
        if (current != null && current.isFromBlueprints()) manager.removeWalls(villageId);
    }

    /** The ground these pieces enclose, or null if they don't enclose any. */
    public static VillageWalls enclose(List<VillageBuildings.Building> pieces) {
        if (pieces.isEmpty()) return null;
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        long sumY = 0;
        for (VillageBuildings.Building b : pieces) {
            minX = Math.min(minX, b.bounds.minX());
            minZ = Math.min(minZ, b.bounds.minZ());
            maxX = Math.max(maxX, b.bounds.maxX());
            maxZ = Math.max(maxZ, b.bounds.maxZ());
            sumY += b.bounds.minY();
        }
        minX -= MARGIN;
        minZ -= MARGIN;
        int width = maxX + MARGIN - minX + 1;
        int depth = maxZ + MARGIN - minZ + 1;
        if ((long) width * depth > MAX_COLUMNS) return null;

        BitSet wall = new BitSet(width * depth);
        for (VillageBuildings.Building b : pieces) {
            Blueprint bp = b.blueprint();
            if (bp == null) continue;
            for (Blueprint.Cell cell : bp.cells(b.rotation)) {
                if (cell.state().isAir()) continue;
                int x = b.origin.getX() + cell.pos().getX() - minX;
                int z = b.origin.getZ() + cell.pos().getZ() - minZ;
                wall.set(x + z * width);
            }
        }

        // Fill from the outside edge; whatever it can't reach (that isn't wall) is enclosed.
        BitSet outside = new BitSet(width * depth);
        int[] queue = new int[width * depth];
        int head = 0, tail = 0;
        for (int x = 0; x < width; x++) {
            tail = seed(x, outside, wall, queue, tail);
            tail = seed(x + (depth - 1) * width, outside, wall, queue, tail);
        }
        for (int z = 0; z < depth; z++) {
            tail = seed(z * width, outside, wall, queue, tail);
            tail = seed(width - 1 + z * width, outside, wall, queue, tail);
        }
        while (head < tail) {
            int i = queue[head++];
            int x = i % width;
            int z = i / width;
            if (x > 0) tail = seed(i - 1, outside, wall, queue, tail);
            if (x < width - 1) tail = seed(i + 1, outside, wall, queue, tail);
            if (z > 0) tail = seed(i - width, outside, wall, queue, tail);
            if (z < depth - 1) tail = seed(i + width, outside, wall, queue, tail);
        }

        BitSet enclosed = new BitSet(width * depth);
        enclosed.set(0, width * depth);
        enclosed.andNot(outside);
        enclosed.andNot(wall);
        if (enclosed.isEmpty()) return null;

        // The walls round the enclosed ground are part of the village too - but not a stray run of
        // wall that goes nowhere, so only wall reachable from the inside within a wall's depth counts.
        BitSet inside = (BitSet) enclosed.clone();
        BitSet ringWall = new BitSet(width * depth);
        head = 0;
        tail = 0;
        int[] dist = new int[width * depth];
        for (int i = enclosed.nextSetBit(0); i >= 0; i = enclosed.nextSetBit(i + 1)) queue[tail++] = i;
        while (head < tail) {
            int i = queue[head++];
            if (dist[i] >= WALL_DEPTH) continue;
            int x = i % width;
            int z = i / width;
            int[] next = {x > 0 ? i - 1 : -1, x < width - 1 ? i + 1 : -1, z > 0 ? i - width : -1, z < depth - 1 ? i + width : -1};
            for (int n : next) {
                if (n < 0 || !wall.get(n) || ringWall.get(n)) continue;
                ringWall.set(n);
                inside.set(n);
                dist[n] = dist[i] + 1;
                queue[tail++] = n;
            }
        }
        return new VillageWalls(minX, minZ, width, depth, (int) (sumY / pieces.size()), inside, ringWall, true);
    }

    private static int seed(int i, BitSet outside, BitSet wall, int[] queue, int tail) {
        if (outside.get(i) || wall.get(i)) return tail;
        outside.set(i);
        queue[tail] = i;
        return tail + 1;
    }
}
