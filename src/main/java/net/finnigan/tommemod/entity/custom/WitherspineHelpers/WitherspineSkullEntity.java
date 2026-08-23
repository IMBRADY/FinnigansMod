package net.finnigan.tommemod.entity.custom.WitherspineHelpers;

import net.finnigan.tommemod.entity.ModEntityTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;

public class WitherspineSkullEntity extends WitherSkull {
    private static final double TRACKING_RANGE = 20.0D;
    private static final double HOMING_STRENGTH = 0.45D;
    private static final double MAX_LEAD_TICKS = 4.0D;
    private LivingEntity lockedTarget;

    public WitherspineSkullEntity(EntityType<? extends WitherSkull> type, Level level) {
        super(type, level);
    }

    public WitherspineSkullEntity(Level level, LivingEntity shooter) {
        this(ModEntityTypes.WITHERSPINE_SKULL.get(), level);
        setOwner(shooter);
        setPos(shooter.getX(), shooter.getEyeY() - 0.1D, shooter.getZ());
        setRot(shooter.getYRot(), shooter.getXRot());
    }

    @Override
    public void tick() {
        if (!level().isClientSide) {
            LivingEntity target = findNearestTarget();
            if (target != null) {
                Vec3 velocity = getDeltaMovement();
                double speed = Math.max(1.25D, velocity.length());
                double leadTicks = Math.min(MAX_LEAD_TICKS, distanceTo(target) / speed);
                Vec3 aimPoint = target.getBoundingBox().getCenter()
                        .add(target.getDeltaMovement().scale(leadTicks));
                Vec3 desired = aimPoint.subtract(position()).normalize();
                Vec3 current = velocity.lengthSqr() > 1.0E-6D ? velocity.normalize() : desired;
                setDeltaMovement(current.scale(1.0D - HOMING_STRENGTH)
                        .add(desired.scale(HOMING_STRENGTH)).normalize().scale(speed));
                xPower = 0.0D;
                yPower = 0.0D;
                zPower = 0.0D;
            }
        }
        super.tick();
    }

    @Override
    protected float getInertia() {
        return 1.0F;
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity target = result.getEntity();
        Entity owner = getOwner();
        boolean hurt;
        if (owner instanceof LivingEntity livingOwner) {
            hurt = target.hurt(damageSources().witherSkull(this, livingOwner), 10.0F);
            if (hurt) {
                if (target.isAlive()) {
                    doEnchantDamageEffects(livingOwner, target);
                } else {
                    livingOwner.heal(5.0F);
                }
            }
        } else {
            hurt = target.hurt(damageSources().magic(), 10.0F);
        }

        if (hurt && target instanceof LivingEntity livingTarget) {
            int seconds = level().getDifficulty() == Difficulty.HARD ? 40
                    : level().getDifficulty() == Difficulty.NORMAL ? 10 : 0;
            if (seconds > 0) {
                livingTarget.addEffect(new MobEffectInstance(MobEffects.WITHER, 20 * seconds, 1),
                        getEffectSource());
            }
        }
    }

    /** Keep the impact explosion, direct hit, and Wither effect without allowing terrain damage. */
    @Override
    protected void onHit(HitResult result) {
        if (result instanceof EntityHitResult entityHit) {
            onHitEntity(entityHit);
        } else if (result instanceof BlockHitResult blockHit) {
            onHitBlock(blockHit);
        }
        if (!level().isClientSide) {
            Entity owner = getOwner();
            boolean ownerWasInvulnerable = owner != null && owner.isInvulnerable();
            if (owner != null) {
                owner.setInvulnerable(true);
            }
            try {
                // The explosion still processes the owner for knockback and all visual/audio effects;
                // temporary invulnerability suppresses only their damage response.
                level().explode(this, getX(), getY(), getZ(), 1.0F, false,
                        Level.ExplosionInteraction.NONE);
            } finally {
                if (owner != null) {
                    owner.setInvulnerable(ownerWasInvulnerable);
                }
            }
            discard();
        }
    }

    private LivingEntity findNearestTarget() {
        Entity owner = getOwner();
        if (lockedTarget != null && lockedTarget.isAlive() && lockedTarget != owner
                && isInOwnersVisionHemisphere(lockedTarget, owner)) {
            return lockedTarget;
        }

        lockedTarget = level().getEntitiesOfClass(LivingEntity.class,
                        (owner != null ? owner.getBoundingBox() : getBoundingBox()).inflate(TRACKING_RANGE),
                        entity -> entity.isAlive() && entity != owner && !entity.isSpectator()
                                && isInOwnersVisionHemisphere(entity, owner))
                .stream().min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
        return lockedTarget;
    }

    private boolean isInOwnersVisionHemisphere(LivingEntity candidate, Entity owner) {
        if (!(owner instanceof LivingEntity livingOwner)) {
            return candidate.distanceToSqr(this) <= TRACKING_RANGE * TRACKING_RANGE;
        }

        Vec3 toTarget = candidate.getBoundingBox().getCenter().subtract(livingOwner.getEyePosition());
        return toTarget.lengthSqr() <= TRACKING_RANGE * TRACKING_RANGE
                && toTarget.dot(livingOwner.getLookAngle()) >= 0.0D;
    }
}
