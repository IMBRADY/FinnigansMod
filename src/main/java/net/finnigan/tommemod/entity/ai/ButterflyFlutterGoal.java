package net.finnigan.tommemod.entity.ai;

import net.finnigan.tommemod.entity.custom.ButterflyEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class ButterflyFlutterGoal extends Goal {
    private final ButterflyEntity butterfly;
    private final double speedModifier;
    // how close (squared distance) counts as "arrived" before picking a new point
    private static final double ARRIVE_DISTANCE_SQ = 1.5D;
    // how far away new wander targets can be picked, in blocks
    private static final double WANDER_RADIUS_HORIZONTAL = 6.0D;
    private static final double WANDER_RADIUS_VERTICAL = 6.0D;

    public ButterflyFlutterGoal(ButterflyEntity butterfly, double speedModifier) {
        this.butterfly = butterfly;
        this.speedModifier = speedModifier*0.6;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        // always true - the butterfly should never be without a flight goal
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
        if (this.butterfly.getNavigation().isDone()
                || this.butterfly.distanceToSqr(
                this.butterfly.getNavigation().getTargetPos().getX(),
                this.butterfly.getNavigation().getTargetPos().getY(),
                this.butterfly.getNavigation().getTargetPos().getZ()) < ARRIVE_DISTANCE_SQ) {
            pickNewTarget();
        }
    }

    private void pickNewTarget() {
        Vec3 current = this.butterfly.position();
        double dx = current.x + (this.butterfly.getRandom().nextDouble() - 0.5D) * 2.0D * WANDER_RADIUS_HORIZONTAL;
        double dy = current.y + (this.butterfly.getRandom().nextDouble() - 0.5D) * 2.0D * WANDER_RADIUS_VERTICAL;
        double dz = current.z + (this.butterfly.getRandom().nextDouble() - 0.5D) * 2.0D * WANDER_RADIUS_HORIZONTAL;

        // keep it from picking a target underground/inside blocks too aggressively
        dy = Mth.clamp(dy, this.butterfly.level().getMinBuildHeight() + 2, this.butterfly.level().getMaxBuildHeight() - 2);

        this.butterfly.getNavigation().moveTo(dx, dy, dz, this.speedModifier);
    }
}