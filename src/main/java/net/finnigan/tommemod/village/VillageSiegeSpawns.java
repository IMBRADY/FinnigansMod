package net.finnigan.tommemod.village;

import net.finnigan.tommemod.config.ModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.levelgen.Heightmap;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Where an attack on a village is allowed to appear.
 *
 * <p>This is the seam every wave type goes through, present and future. A walled village is meant to
 * be worth the logs it cost: whatever is coming has to arrive outside the wall and walk in through
 * whatever gap it can find, rather than materialising in the square behind it. Rather than teaching
 * each attacker about walls, spawn logic hands its chosen position to {@link #pushOutsideWalls} and
 * gets back one the walls agree with - so a wave type added later inherits the behaviour by using the
 * same call, and nothing has to be revisited when the wall design changes.
 *
 * <p>Raids are wired in through {@code RaidWallSpawnMixin}, which routes vanilla's own choice of
 * wave position through here.
 */
public final class VillageSiegeSpawns {

    /** How far past the wall a pushed-out spawn keeps walking before it stops looking. */
    private static final int SEARCH_OVERSHOOT_BLOCKS = 96;
    /** Blocks per step while walking outward. Coarse: this is a search, not a path. */
    private static final int STEP_BLOCKS = 4;
    /** Directions tried before giving up on finding solid ground and taking the shortest way out. */
    private static final int DIRECTIONS_TRIED = 8;

    private VillageSiegeSpawns() {
    }

    /**
     * The given position if no village's walls enclose it, or the nearest position outside those
     * walls if some village's do.
     *
     * <p>Never returns a position still inside the ring. If nothing outside it passes the same
     * ground check vanilla applies to a raid spawn, the un-vetted outside position wins anyway: a
     * raider landing awkwardly outside the wall is the mechanic working, and one landing neatly
     * inside it is the mechanic not existing.
     */
    public static BlockPos pushOutsideWalls(ServerLevel level, BlockPos proposed) {
        VillageWalls walls = VillageManager.get(level).wallsContaining(proposed);
        if (walls == null) return proposed;

        BlockPos centre = walls.centre();
        double dx = proposed.getX() - centre.getX();
        double dz = proposed.getZ() - centre.getZ();
        // A spawn that landed dead on the ring's centre gives no direction to leave by; any will do.
        double bearing = dx == 0 && dz == 0 ? level.getRandom().nextDouble() * Math.PI * 2 : Math.atan2(dz, dx);

        BlockPos fallback = null;
        for (int i = 0; i < DIRECTIONS_TRIED; i++) {
            // Straight out first, then fanning either side of it, so the wave still arrives from
            // roughly where vanilla wanted it to when the terrain that way is unusable.
            double angle = bearing + fanOffset(i);
            BlockPos candidate = walkOut(level, walls, centre, angle);
            if (candidate == null) continue;
            if (fallback == null) fallback = candidate;
            if (isStandable(level, candidate)) return candidate;
        }
        return fallback != null ? fallback : proposed;
    }

    /**
     * A fresh spawn point outside this village, for callers choosing one from scratch rather than
     * correcting a position they already had. Falls back to the village's POI footprint when it has
     * no walls, so an unwalled village still gets attacked from outside itself.
     */
    @Nullable
    public static BlockPos outsideVillage(ServerLevel level, UUID villageId, RandomSource random) {
        VillageManager manager = VillageManager.get(level);
        VillageRegion region = manager.resolveVillageRegion(level, villageId);

        double angle = random.nextDouble() * Math.PI * 2;
        double reach = region.radius() + ModConfig.VILLAGE_WALLS_SPAWN_MARGIN_BLOCKS.get();
        int x = region.anchor().getX() + Mth.floor(Math.cos(angle) * reach);
        int z = region.anchor().getZ() + Mth.floor(Math.sin(angle) * reach);

        BlockPos surface = surfaceAt(level, x, z);
        return surface == null ? null : pushOutsideWalls(level, surface);
    }

    /** 0, then +/- one step, then +/- two, and so on - a fan centred on the requested bearing. */
    private static double fanOffset(int attempt) {
        int magnitude = (attempt + 1) / 2;
        double step = Math.PI / 6;
        return (attempt % 2 == 0 ? 1 : -1) * magnitude * step;
    }

    /**
     * Walks outward from the ring's centre along one bearing until it is clear of the wall by the
     * configured margin, and returns the ground there. Null if it walked off the loaded world first.
     */
    @Nullable
    private static BlockPos walkOut(ServerLevel level, VillageWalls walls, BlockPos centre, double angle) {
        int margin = ModConfig.VILLAGE_WALLS_SPAWN_MARGIN_BLOCKS.get();
        int limit = (int) Math.ceil(walls.radiusFromCentre()) + margin + SEARCH_OVERSHOOT_BLOCKS;
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);

        for (int distance = 0; distance <= limit; distance += STEP_BLOCKS) {
            int x = centre.getX() + Mth.floor(cos * distance);
            int z = centre.getZ() + Mth.floor(sin * distance);
            if (walls.contains(x, z)) continue;

            // Clear of the ring - now back off the margin's worth further before settling.
            int x2 = centre.getX() + Mth.floor(cos * (distance + margin));
            int z2 = centre.getZ() + Mth.floor(sin * (distance + margin));
            return surfaceAt(level, x2, z2);
        }
        return null;
    }

    @Nullable
    private static BlockPos surfaceAt(ServerLevel level, int x, int z) {
        if (!level.hasChunk(x >> 4, z >> 4)) return null;
        return new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z), z);
    }

    /** Vanilla's own test for whether a raid wave can stand somewhere, asked the same way. */
    private static boolean isStandable(ServerLevel level, BlockPos pos) {
        if (!level.hasChunksAt(pos.getX() - 10, pos.getZ() - 10, pos.getX() + 10, pos.getZ() + 10)) return false;
        if (!level.isPositionEntityTicking(pos)) return false;
        return NaturalSpawner.isSpawnPositionOk(SpawnPlacements.Type.ON_GROUND, level, pos, EntityType.RAVAGER);
    }
}
