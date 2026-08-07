package net.finnigan.tommemod.entity.custom.FalconHelpers;

import net.finnigan.tommemod.entity.custom.FalconEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Grounded movement, deliberately identical in feel to BirdieEntity's hop: a single up-and-forward
 * impulse whose horizontal travel lasts exactly as long as the short "hop" animation, and nothing in
 * between - so a landed falcon reads as hop, idle, hop, idle rather than sliding around.
 */
public class FalconHopGoal extends Goal {

    /** Ticks of forward travel per hop - matches the 0.125s "hop" animation. */
    private static final int HOP_BURST_TICKS = 3;
    private static final double HOP_UP_VELOCITY = 0.32;
    private static final double HOP_FORWARD_SPEED = 0.14;

    private final FalconEntity falcon;
    private final double speedModifier;
    private double targetX, targetZ;
    private int hopBurstTicks;
    private boolean hasHopped;

    public FalconHopGoal(FalconEntity falcon, double speedModifier) {
        this.falcon = falcon;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (falcon.isFlying() || falcon.isDiving() || !falcon.onGround()) return false;
        return falcon.getRandom().nextInt(60) == 0; // occasional, not constant
    }

    @Override
    public boolean canContinueToUse() {
        return !hasHopped;
    }

    @Override
    public void start() {
        Vec3 pos = falcon.position();
        double angle = falcon.getRandom().nextDouble() * Math.PI * 2;
        targetX = pos.x + Math.cos(angle) * 1.5;
        targetZ = pos.z + Math.sin(angle) * 1.5;
        hasHopped = false;
        hopBurstTicks = 0;
    }

    @Override
    public void stop() {
        falcon.setHopping(false);
    }

    @Override
    public void tick() {
        Vec3 pos = falcon.position();

        if (hopBurstTicks > 0) {
            hopBurstTicks--;
            falcon.setHopping(true);
            Vec3 dir = new Vec3(targetX - pos.x, 0, targetZ - pos.z).normalize();
            falcon.move(MoverType.SELF, new Vec3(dir.x * HOP_FORWARD_SPEED * speedModifier, 0.0,
                    dir.z * HOP_FORWARD_SPEED * speedModifier));
            falcon.getLookControl().setLookAt(targetX, pos.y, targetZ);
            faceMovementDirection(dir);

            if (hopBurstTicks == 0) {
                hasHopped = true;
            }
        } else if (falcon.onGround() && !hasHopped) {
            falcon.setDeltaMovement(falcon.getDeltaMovement().x, HOP_UP_VELOCITY, falcon.getDeltaMovement().z);
            hopBurstTicks = HOP_BURST_TICKS;
        }
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
