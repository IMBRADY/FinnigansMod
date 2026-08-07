package net.finnigan.tommemod.entity.ai;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Reusable version of {@link ButterflyFlutterGoal} for any flying PathfinderMob: forever picks a new
 * nearby point and paths to it through the mob's FlyingPathNavigation, so the mob drifts around
 * indefinitely and - because it is the navigator picking the route, not raw velocity - never flies
 * into blocks.
 */
public class FlutterGoal extends Goal {

    // how close (squared distance) counts as "arrived" before picking a new point
    private static final double ARRIVE_DISTANCE_SQ = 1.5D;

    private final PathfinderMob mob;
    private final double speedModifier;
    private final double horizontalRadius;
    private final double verticalRadius;

    public FlutterGoal(PathfinderMob mob, double speedModifier, double horizontalRadius, double verticalRadius) {
        this.mob = mob;
        this.speedModifier = speedModifier;
        this.horizontalRadius = horizontalRadius;
        this.verticalRadius = verticalRadius;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        // always true - these mobs should never be without a flight goal
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return true;
    }

    @Override
    public void start() {
        pickNewTarget();
    }

    @Override
    public void tick() {
        // isDone() is checked first so getTargetPos() is only read while a path actually exists
        if (this.mob.getNavigation().isDone()
                || this.mob.distanceToSqr(Vec3.atCenterOf(this.mob.getNavigation().getTargetPos())) < ARRIVE_DISTANCE_SQ) {
            pickNewTarget();
        }
    }

    private void pickNewTarget() {
        Vec3 current = this.mob.position();
        double x = current.x + (this.mob.getRandom().nextDouble() - 0.5D) * 2.0D * this.horizontalRadius;
        double y = current.y + (this.mob.getRandom().nextDouble() - 0.5D) * 2.0D * this.verticalRadius;
        double z = current.z + (this.mob.getRandom().nextDouble() - 0.5D) * 2.0D * this.horizontalRadius;

        // keep it from picking a target underground / out of the world
        y = Mth.clamp(y, this.mob.level().getMinBuildHeight() + 2, this.mob.level().getMaxBuildHeight() - 2);

        this.mob.getNavigation().moveTo(x, y, z, this.speedModifier);
    }
}
