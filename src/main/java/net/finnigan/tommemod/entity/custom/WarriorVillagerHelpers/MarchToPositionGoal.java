package net.finnigan.tommemod.entity.custom.WarriorVillagerHelpers;

import net.finnigan.tommemod.entity.custom.WarriorVillagerEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * Walks a Warrior somewhere it is needed but cannot see - a cry for help across the village, or the
 * side of it a raid is about to come from.
 *
 * The hopping is not decoration. A mob's navigation only searches out to its FOLLOW_RANGE (24 blocks
 * for a Warrior), so asking one to path eighty blocks across a village simply returns no path at all
 * and it stands still. Marching in short legs and re-aiming on arrival keeps every request well inside
 * what the pathfinder will actually answer, and re-aiming from the new position each leg is what makes
 * it follow terrain rather than insist on the straight line it started with.
 *
 * Always yields to fighting: a Warrior with something in front of it has no business marching.
 */
public abstract class MarchToPositionGoal extends Goal {

    /** Length of one leg of the march. Comfortably inside FOLLOW_RANGE so paths actually resolve. */
    private static final double LEG_LENGTH = 12.0;
    /** Close enough to count as arrived - Warriors are meant to gather, not stand on one block. */
    private static final double DEFAULT_ARRIVAL_DISTANCE = 4.0;
    /** How often the march re-aims even if the current leg is still being walked. */
    private static final int REAIM_INTERVAL_TICKS = 20;

    protected final WarriorVillagerEntity warrior;
    private final double speedModifier;

    @Nullable
    private BlockPos destination;
    private int reaimCooldown;

    protected MarchToPositionGoal(WarriorVillagerEntity warrior, double speedModifier) {
        this.warrior = warrior;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    /** Where this Warrior ought to be standing, or null if it has nowhere in particular to be. */
    @Nullable
    protected abstract BlockPos destination();

    /** How near the destination is near enough. Tighten it where the destination is already spread
     * per-Warrior, so slop and spread don't compound into a much wider scatter than intended. */
    protected double arrivalDistance() {
        return DEFAULT_ARRIVAL_DISTANCE;
    }

    /** Only the unoccupied march - anyone already in a fight stays in it. */
    private boolean isUnoccupied() {
        LivingEntity target = warrior.getTarget();
        return target == null || !target.isAlive();
    }

    @Override
    public boolean canUse() {
        if (!isUnoccupied()) return false;

        destination = destination();
        return destination != null && !hasArrived();
    }

    @Override
    public boolean canContinueToUse() {
        if (!isUnoccupied()) return false;

        // Re-read rather than trusting the destination we set out for: a fresher cry for help, or a
        // raid re-picking where its wave lands, should redirect a Warrior already on the move.
        destination = destination();
        return destination != null && !hasArrived();
    }

    @Override
    public void start() {
        reaimCooldown = 0;
    }

    @Override
    public void stop() {
        warrior.getNavigation().stop();
        destination = null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (destination == null) return;

        warrior.getLookControl().setLookAt(
                destination.getX() + 0.5, destination.getY() + 0.5, destination.getZ() + 0.5);

        if (--reaimCooldown > 0 && !warrior.getNavigation().isDone()) return;
        reaimCooldown = REAIM_INTERVAL_TICKS;

        BlockPos leg = nextLeg(destination);
        warrior.getNavigation().moveTo(leg.getX() + 0.5, leg.getY(), leg.getZ() + 0.5, speedModifier);
    }

    private boolean hasArrived() {
        double arrival = arrivalDistance();
        return destination != null
                && warrior.distanceToSqr(Vec3.atCenterOf(destination)) <= arrival * arrival;
    }

    /**
     * The end of the current leg: the destination itself once it is within reach, otherwise a point
     * one leg along the way. Held at the Warrior's own height rather than snapped to the surface,
     * since a waypoint is only ever a direction to set off in - the pathfinder settles on ground it
     * can actually stand on, and the next leg re-aims from wherever that turned out to be.
     */
    private BlockPos nextLeg(BlockPos target) {
        Vec3 from = warrior.position();
        Vec3 to = Vec3.atCenterOf(target);
        double distance = from.distanceTo(to);
        if (distance <= LEG_LENGTH) return target;

        Vec3 step = from.add(to.subtract(from).scale(LEG_LENGTH / distance));
        return BlockPos.containing(step.x, warrior.getY(), step.z);
    }
}
