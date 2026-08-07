package net.finnigan.tommemod.entity.custom.FalconHelpers;

import net.finnigan.tommemod.entity.custom.FalconEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.AbstractFish;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/**
 * The falcon's hunt. Fires only when a fish is (a) below the bird, (b) within a 5-block horizontal
 * radius, and (c) sitting no more than one block under the surface of its own body of water - the
 * three conditions from the design brief. It then drops straight onto the fish playing "dive" and
 * kills it on contact.
 */
public class FalconDiveGoal extends Goal {

    private static final double SEARCH_RADIUS = 5.0;
    private static final double SEARCH_DEPTH = 12.0;
    /** How far under the surface a fish may be and still be catchable. */
    private static final int MAX_DEPTH_BELOW_SURFACE = 1;
    private static final double DIVE_SPEED = 0.55;
    private static final double CATCH_DISTANCE = 1.2;
    private static final float CATCH_DAMAGE = 100.0F;
    private static final int MAX_DIVE_TICKS = 60;
    private static final int DIVE_COOLDOWN_TICKS = 100;

    private final FalconEntity falcon;
    private AbstractFish quarry;
    private int diveTicks;
    private int cooldown;

    public FalconDiveGoal(FalconEntity falcon) {
        this.falcon = falcon;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        if (!falcon.isFlying()) return false;
        quarry = findQuarry();
        return quarry != null;
    }

    @Override
    public boolean canContinueToUse() {
        return quarry != null && quarry.isAlive() && diveTicks < MAX_DIVE_TICKS;
    }

    @Override
    public void start() {
        diveTicks = 0;
        falcon.setDiving(true);
        falcon.getNavigation().stop();
    }

    @Override
    public void stop() {
        falcon.setDiving(false);
        quarry = null;
        cooldown = DIVE_COOLDOWN_TICKS;
        // Come out of the dive still airborne so the patrol goal picks straight back up.
        falcon.setFlying(true);
        falcon.setDeltaMovement(Vec3.ZERO);
    }

    @Override
    public void tick() {
        if (quarry == null) return;
        diveTicks++;

        Vec3 toQuarry = quarry.position().subtract(falcon.position());
        falcon.getLookControl().setLookAt(quarry, 30.0F, 30.0F);
        faceMovementDirection(toQuarry);

        if (toQuarry.length() <= CATCH_DISTANCE) {
            quarry.hurt(falcon.damageSources().mobAttack(falcon), CATCH_DAMAGE);
            falcon.startEating();
            quarry = null;
            return;
        }

        Vec3 motion = toQuarry.normalize().scale(DIVE_SPEED);
        falcon.setDeltaMovement(motion);
        falcon.move(MoverType.SELF, motion);
    }

    private AbstractFish findQuarry() {
        Level level = falcon.level();
        AABB searchBox = falcon.getBoundingBox().inflate(SEARCH_RADIUS, 0.0, SEARCH_RADIUS)
                .expandTowards(0.0, -SEARCH_DEPTH, 0.0);

        List<AbstractFish> candidates = level.getEntitiesOfClass(AbstractFish.class, searchBox, fish ->
                fish.isAlive()
                        && fish.getY() < falcon.getY()
                        && horizontalDistanceSqr(fish) <= SEARCH_RADIUS * SEARCH_RADIUS
                        && isNearWaterSurface(level, fish));

        return candidates.stream().min(Comparator.comparingDouble(falcon::distanceToSqr)).orElse(null);
    }

    private double horizontalDistanceSqr(AbstractFish fish) {
        double dx = fish.getX() - falcon.getX();
        double dz = fish.getZ() - falcon.getZ();
        return dx * dx + dz * dz;
    }

    /**
     * Walks up the fish's own column to find where its water actually ends, so "1 block below water
     * level" means the local surface rather than sea level - it works the same in a pond on a hill.
     */
    private static boolean isNearWaterSurface(Level level, AbstractFish fish) {
        BlockPos pos = fish.blockPosition();
        if (!level.getFluidState(pos).is(FluidTags.WATER)) return false;

        BlockPos.MutableBlockPos cursor = pos.mutable();
        int surfaceY = pos.getY();
        for (int i = 0; i <= MAX_DEPTH_BELOW_SURFACE + 1; i++) {
            cursor.move(Direction.UP);
            if (!level.getFluidState(cursor).is(FluidTags.WATER)) break;
            surfaceY = cursor.getY();
        }
        return pos.getY() >= surfaceY - MAX_DEPTH_BELOW_SURFACE;
    }

    private void faceMovementDirection(Vec3 motion) {
        if (motion.horizontalDistanceSqr() < 1.0E-5) return;
        float targetYaw = (float) (Mth.atan2(-motion.x, motion.z) * (180.0 / Math.PI));
        float newYaw = Mth.rotateIfNecessary(falcon.getYRot(), targetYaw, 25.0F);
        falcon.setYRot(newYaw);
        falcon.yBodyRot = newYaw;
        falcon.setYHeadRot(newYaw);
    }
}
