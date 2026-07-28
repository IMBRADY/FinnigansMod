package net.finnigan.tommemod.entity.custom.CyclopsHelpers;

import net.finnigan.tommemod.entity.custom.Bosses.BossCrab.CrabHitboxUtil;
import net.finnigan.tommemod.entity.custom.CyclopsEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * When the target is at "medium" range (too far to just swing, close enough to reach in one go),
 * winds up briefly, then lunges: low vertical velocity, fast horizontal velocity straight at the
 * target (see BossCrabEntity's jump for the reference physics), dealing damage to whatever's nearby
 * the instant it lands.
 */
public class CyclopsLungeGoal extends Goal {

    private static final double LUNGE_MIN_RANGE = 4.5;
    private static final double LUNGE_MAX_RANGE = 10.0;
    private static final int WINDUP_TICKS = 5; // matches lunge's initial crouch (~0.25s)
    private static final double LUNGE_UP_VELOCITY = 0.32;      // "little vertical velocity"
    private static final double MAX_HORIZONTAL_SPEED = 1.1;    // "fast horizontal velocity"
    private static final double GRAVITY_APPROX = 0.08;
    private static final double IMPACT_RADIUS = 2.25;
    private static final double IMPACT_VERTICAL_REACH = 2.0;
    private static final int MAX_AIRBORNE_TICKS = 30; // safety timeout so it can't get stuck mid-air

    private final CyclopsEntity mob;
    private int windupTicksRemaining = -1;
    private boolean airborne;
    private int airborneTicks;
    private Vec3 horizontalVelocity = Vec3.ZERO;

    public CyclopsLungeGoal(CyclopsEntity mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (!mob.isLungeReady() || !mob.onGround()) return false;
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) return false;
        double distSqr = mob.distanceToSqr(target);
        return distSqr >= LUNGE_MIN_RANGE * LUNGE_MIN_RANGE && distSqr <= LUNGE_MAX_RANGE * LUNGE_MAX_RANGE;
    }

    @Override
    public boolean canContinueToUse() {
        return windupTicksRemaining >= 0 || airborne;
    }

    @Override
    public void start() {
        windupTicksRemaining = WINDUP_TICKS;
        airborne = false;
        mob.getNavigation().stop();
        mob.setLunging(true);
        mob.triggerAnim("attackController", "lunge");
    }

    @Override
    public void stop() {
        airborne = false;
        windupTicksRemaining = -1;
        mob.setLunging(false);
        mob.startLungeCooldown();
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) return;

        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

        if (windupTicksRemaining >= 0) {
            windupTicksRemaining--;
            if (windupTicksRemaining < 0) {
                launch(target);
            }
            return;
        }

        if (airborne) {
            airborneTicks++;
            if (!mob.onGround()) {
                mob.setDeltaMovement(horizontalVelocity.x, mob.getDeltaMovement().y, horizontalVelocity.z);
            }
            if (mob.onGround() || airborneTicks > MAX_AIRBORNE_TICKS) {
                landImpact();
                airborne = false;
            }
        }
    }

    private void launch(LivingEntity target) {
        Vec3 toTarget = target.position().subtract(mob.position());
        Vec3 flat = new Vec3(toTarget.x, 0, toTarget.z);
        double dist = flat.length();
        Vec3 dir = dist > 1.0E-4 ? flat.scale(1.0 / dist) : mob.getLookAngle();

        double horizontalSpeed = Math.min(MAX_HORIZONTAL_SPEED, dist * (GRAVITY_APPROX / (2.0 * LUNGE_UP_VELOCITY)) * 2.0);
        horizontalVelocity = new Vec3(dir.x * horizontalSpeed, 0, dir.z * horizontalSpeed);

        mob.setDeltaMovement(horizontalVelocity.x, LUNGE_UP_VELOCITY, horizontalVelocity.z);
        mob.hasImpulse = true;

        float yaw = (float) (Mth.atan2(dir.z, dir.x) * (180.0 / Math.PI)) - 90.0F;
        mob.setYRot(yaw);
        mob.yBodyRot = yaw;
        mob.yHeadRot = yaw;

        airborne = true;
        airborneTicks = 0;
    }

    private void landImpact() {
        double damage = mob.getAttributeValue(Attributes.ATTACK_DAMAGE);
        for (LivingEntity nearby : CrabHitboxUtil.getNearbyLivingExcludingSelf(mob, IMPACT_RADIUS)) {
            if (CrabHitboxUtil.isInCylinder(mob, nearby, IMPACT_RADIUS, IMPACT_VERTICAL_REACH)) {
                nearby.hurt(mob.damageSources().mobAttack(mob), (float) damage);
                Vec3 knockback = nearby.position().subtract(mob.position()).normalize().scale(0.6).add(0, 0.25, 0);
                nearby.push(knockback.x, knockback.y, knockback.z);
            }
        }
    }
}
