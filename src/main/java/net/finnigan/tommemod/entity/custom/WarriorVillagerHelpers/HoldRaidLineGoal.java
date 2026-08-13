package net.finnigan.tommemod.entity.custom.WarriorVillagerHelpers;

import net.finnigan.tommemod.entity.custom.WarriorVillagerEntity;
import net.finnigan.tommemod.mixin.RaidAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Marches idle Warriors to the side of the village the next raid wave is about to come from, so the
 * defence is already standing there when it arrives instead of running the width of the village
 * afterwards.
 *
 * The foreknowledge is real rather than invented: vanilla settles on a wave's spawn point during the
 * lull before it - up to fifteen seconds ahead - and only clears it as the raiders actually appear.
 * Reading it through RaidAccessor turns that lull into marching orders, and the same fact going quiet
 * is what ends them. Note the spawn point can be the better part of a hundred blocks out, which is why
 * Warriors form up at the edge of the village facing it rather than walking out to meet it.
 */
public class HoldRaidLineGoal extends MarchToPositionGoal {

    /** How far out from the village centre the line forms, in blocks. */
    private static final double HOLD_DISTANCE = 20.0;
    /** How far off the line a Warrior may be posted, so they hold a front rather than a single block. */
    private static final double SPREAD_RADIUS = 3.0;
    /** Tighter than the default march slop, so spread and slop together stay inside 5 blocks. */
    private static final double ARRIVAL_DISTANCE = 2.0;

    public HoldRaidLineGoal(WarriorVillagerEntity warrior, double speedModifier) {
        super(warrior, speedModifier);
    }

    @Override
    protected double arrivalDistance() {
        return ARRIVAL_DISTANCE;
    }

    @Nullable
    @Override
    protected BlockPos destination() {
        if (!(warrior.level() instanceof ServerLevel level)) return null;

        Raid raid = level.getRaidAt(warrior.blockPosition());
        if (raid == null || raid.isStopped() || raid.isOver()) return null;
        // Once raiders are on the ground there is nothing left to anticipate - fight what is here.
        if (raid.getTotalRaidersAlive() > 0) return null;

        BlockPos waveSpawn = ((RaidAccessor) raid).tommemod$getWaveSpawnPos().orElse(null);
        if (waveSpawn == null) return null; // between raids, or the wave is already on its way in

        Vec3 centre = Vec3.atCenterOf(raid.getCenter());
        Vec3 approach = Vec3.atCenterOf(waveSpawn).subtract(centre);
        double distance = Math.sqrt(approach.x * approach.x + approach.z * approach.z);
        if (distance < 1.0) return null; // wave landing on top of the village - nowhere to form up

        double scale = Math.min(HOLD_DISTANCE, distance) / distance;
        BlockPos line = BlockPos.containing(centre.x + approach.x * scale, centre.y, centre.z + approach.z * scale);
        return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ownPostOn(line));
    }

    /**
     * This Warrior's own spot on the line, so a squad forms a front instead of a pile on one block.
     *
     * Derived from the Warrior's identity rather than rolled fresh: the destination is re-read every
     * tick while marching, so a new roll each time would send it after a point that never sits still.
     * Same Warrior, same post, for as long as the wave is coming.
     */
    private BlockPos ownPostOn(BlockPos line) {
        int hash = warrior.getUUID().hashCode();
        double angle = (hash & 0xFFFF) / 65536.0 * Math.PI * 2.0;
        double radius = ((hash >>> 16) & 0xFF) / 255.0 * SPREAD_RADIUS;
        return line.offset((int) Math.round(Math.cos(angle) * radius), 0, (int) Math.round(Math.sin(angle) * radius));
    }
}
