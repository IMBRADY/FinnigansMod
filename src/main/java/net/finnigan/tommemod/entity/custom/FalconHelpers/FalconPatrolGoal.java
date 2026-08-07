package net.finnigan.tommemod.entity.custom.FalconHelpers;

import net.finnigan.tommemod.entity.custom.FalconEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Keeps the falcon airborne almost all the time, tracing long patrol legs that prefer to pass over
 * open water when there is any nearby (that is what puts it in position for FalconDiveGoal) and
 * otherwise wander over the treetops. Same raw-velocity + forward clip-check flight BirdieEntity uses,
 * which is why the falcon steers around blocks instead of pathing through them.
 */
public class FalconPatrolGoal extends Goal {

    private static final double FLIGHT_SPEED = 0.22;
    private static final int WATER_SCAN_RADIUS = 16;
    private static final int WATER_SCAN_ATTEMPTS = 24;
    /** How far above the surface the falcon holds station while hunting. */
    private static final double PATROL_HEIGHT_OVER_WATER = 5.0;

    private final FalconEntity falcon;
    private double targetX, targetY, targetZ;
    private int flightTimer;
    private int groundedCooldown = 20;

    public FalconPatrolGoal(FalconEntity falcon) {
        this.falcon = falcon;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (falcon.isDiving()) return false;
        if (falcon.isFlying()) return true;
        if (groundedCooldown-- > 0) return false;
        return falcon.getRandom().nextInt(10) == 0;
    }

    @Override
    public boolean canContinueToUse() {
        return falcon.isFlying() && !falcon.isDiving() && flightTimer > 0;
    }

    @Override
    public void start() {
        falcon.setFlying(true);
        falcon.getNavigation().stop();
        pickNewTarget();
        flightTimer = 600 + falcon.getRandom().nextInt(600); // 30-60s patrol legs
    }

    @Override
    public void stop() {
        falcon.setFlying(false);
        falcon.setDeltaMovement(falcon.getDeltaMovement().x, 0, falcon.getDeltaMovement().z);
        groundedCooldown = 40 + falcon.getRandom().nextInt(60);
    }

    @Override
    public void tick() {
        flightTimer--;

        Vec3 pos = falcon.position();
        Vec3 diff = new Vec3(targetX, targetY, targetZ).subtract(pos);

        if (diff.lengthSqr() < 1.0) {
            pickNewTarget();
        } else {
            Vec3 motion = diff.normalize().scale(FLIGHT_SPEED);
            // look a little ahead rather than a single tick's worth, so it banks early around terrain
            if (isPathBlocked(pos, pos.add(motion.scale(4.0)))) {
                pickNewTarget();
                return;
            }
            falcon.setDeltaMovement(motion);
            falcon.move(MoverType.SELF, falcon.getDeltaMovement());
            falcon.getLookControl().setLookAt(targetX, targetY, targetZ);
            faceMovementDirection(motion);
        }

        if (flightTimer <= 0) {
            falcon.setFlying(false);
        }
    }

    private void pickNewTarget() {
        BlockPos water = findNearbyWaterSurface();
        if (water != null) {
            targetX = water.getX() + 0.5;
            targetY = water.getY() + PATROL_HEIGHT_OVER_WATER;
            targetZ = water.getZ() + 0.5;
            return;
        }

        Vec3 pos = falcon.position();
        double angle = falcon.getRandom().nextDouble() * Math.PI * 2;
        double dist = 6.0 + falcon.getRandom().nextDouble() * 10.0;
        targetX = pos.x + Math.cos(angle) * dist;
        targetY = pos.y + 1.0 + falcon.getRandom().nextDouble() * 4.0; // lean upward, away from obstacles
        targetZ = pos.z + Math.sin(angle) * dist;
    }

    /** Random-sample the area for a water surface block; null if this patch of world has no water. */
    private BlockPos findNearbyWaterSurface() {
        Level level = falcon.level();
        BlockPos origin = falcon.blockPosition();
        for (int i = 0; i < WATER_SCAN_ATTEMPTS; i++) {
            BlockPos candidate = origin.offset(
                    falcon.getRandom().nextInt(WATER_SCAN_RADIUS * 2) - WATER_SCAN_RADIUS,
                    falcon.getRandom().nextInt(9) - 6,
                    falcon.getRandom().nextInt(WATER_SCAN_RADIUS * 2) - WATER_SCAN_RADIUS);
            if (level.getFluidState(candidate).is(FluidTags.WATER)
                    && level.getFluidState(candidate.above()).isEmpty()) {
                return candidate;
            }
        }
        return null;
    }

    private boolean isPathBlocked(Vec3 from, Vec3 to) {
        ClipContext context = new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, falcon);
        return falcon.level().clip(context).getType() == HitResult.Type.BLOCK;
    }

    private void faceMovementDirection(Vec3 motion) {
        if (motion.horizontalDistanceSqr() < 1.0E-5) return;
        float targetYaw = (float) (Mth.atan2(-motion.x, motion.z) * (180.0 / Math.PI));
        float newYaw = Mth.rotateIfNecessary(falcon.getYRot(), targetYaw, 15.0F);
        falcon.setYRot(newYaw);
        falcon.yBodyRot = newYaw;
        falcon.setYHeadRot(newYaw);
    }
}
