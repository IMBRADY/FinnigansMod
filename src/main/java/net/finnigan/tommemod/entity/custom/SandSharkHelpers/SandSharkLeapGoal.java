package net.finnigan.tommemod.entity.custom.SandSharkHelpers;

import net.finnigan.tommemod.entity.custom.SandSharkEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * The sand shark's gap-closer, built on the same physics as CyclopsLungeGoal: a brief wind-up, then a
 * low, fast arc at the target that hurts whatever it lands on. Only fires from long range, so at close
 * quarters the shark just bites.
 */
public class SandSharkLeapGoal extends Goal {

    /** "Subtle cooldown" - long enough that the leap is a punctuation mark, not the whole fight. */
    public static final int LEAP_COOLDOWN_TICKS = 100;

    private static final double LEAP_MIN_RANGE = 6.0;
    private static final double LEAP_MAX_RANGE = 16.0;
    private static final int WINDUP_TICKS = 6;
    private static final double LEAP_UP_VELOCITY = 0.42;
    private static final double MAX_HORIZONTAL_SPEED = 1.3;
    private static final double GRAVITY_APPROX = 0.08;
    private static final double IMPACT_RADIUS = 2.0;
    private static final int MAX_AIRBORNE_TICKS = 40; // safety timeout so it can't hang mid-air

    private final SandSharkEntity shark;
    private int windupTicksRemaining = -1;
    private boolean airborne;
    private int airborneTicks;
    private Vec3 horizontalVelocity = Vec3.ZERO;

    public SandSharkLeapGoal(SandSharkEntity shark) {
        this.shark = shark;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (!shark.isLeapReady() || !shark.onGround()) return false;
        LivingEntity target = shark.getTarget();
        if (target == null || !target.isAlive()) return false;
        double distanceSqr = shark.distanceToSqr(target);
        return distanceSqr >= LEAP_MIN_RANGE * LEAP_MIN_RANGE && distanceSqr <= LEAP_MAX_RANGE * LEAP_MAX_RANGE;
    }

    @Override
    public boolean canContinueToUse() {
        return windupTicksRemaining >= 0 || airborne;
    }

    @Override
    public void start() {
        windupTicksRemaining = WINDUP_TICKS;
        airborne = false;
        shark.getNavigation().stop();
        shark.setLeaping(true);
        shark.triggerAnim("actionController", "jump");
    }

    @Override
    public void stop() {
        airborne = false;
        windupTicksRemaining = -1;
        shark.setLeaping(false);
        shark.startLeapCooldown();
    }

    @Override
    public void tick() {
        LivingEntity target = shark.getTarget();
        if (target == null) return;

        shark.getLookControl().setLookAt(target, 30.0F, 30.0F);

        if (windupTicksRemaining >= 0) {
            windupTicksRemaining--;
            if (windupTicksRemaining < 0) {
                launch(target);
            }
            return;
        }

        if (airborne) {
            airborneTicks++;
            if (!shark.onGround()) {
                shark.setDeltaMovement(horizontalVelocity.x, shark.getDeltaMovement().y, horizontalVelocity.z);
            }
            if (shark.onGround() || airborneTicks > MAX_AIRBORNE_TICKS) {
                landImpact();
                airborne = false;
            }
        }
    }

    private void launch(LivingEntity target) {
        Vec3 flat = new Vec3(target.getX() - shark.getX(), 0, target.getZ() - shark.getZ());
        double distance = flat.length();
        Vec3 direction = distance > 1.0E-4 ? flat.scale(1.0 / distance) : shark.getLookAngle();

        double horizontalSpeed = Math.min(MAX_HORIZONTAL_SPEED,
                distance * (GRAVITY_APPROX / (2.0 * LEAP_UP_VELOCITY)) * 2.0);
        horizontalVelocity = new Vec3(direction.x * horizontalSpeed, 0, direction.z * horizontalSpeed);

        shark.setDeltaMovement(horizontalVelocity.x, LEAP_UP_VELOCITY, horizontalVelocity.z);
        shark.hasImpulse = true;

        float yaw = (float) (Mth.atan2(direction.z, direction.x) * (180.0 / Math.PI)) - 90.0F;
        shark.setYRot(yaw);
        shark.yBodyRot = yaw;
        shark.yHeadRot = yaw;

        airborne = true;
        airborneTicks = 0;
    }

    private void landImpact() {
        double damage = shark.getAttributeValue(Attributes.ATTACK_DAMAGE);
        for (LivingEntity nearby : shark.level().getEntitiesOfClass(LivingEntity.class,
                shark.getBoundingBox().inflate(IMPACT_RADIUS), other -> other != shark && other.isAlive())) {
            nearby.hurt(shark.damageSources().mobAttack(shark), (float) damage);
            Vec3 knockback = nearby.position().subtract(shark.position()).normalize().scale(0.5).add(0, 0.2, 0);
            nearby.push(knockback.x, knockback.y, knockback.z);
        }
    }
}
