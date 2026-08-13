package net.finnigan.tommemod.entity.custom.ShadowSwordHelpers;

import net.finnigan.tommemod.entity.ModEntityTypes;
import net.finnigan.tommemod.event.ShadowSwordHelpers.ShadowSoulManager;
import net.finnigan.tommemod.item.custom.FireKatanaItem;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * One of the Shadow Sword's thrown souls: flies straight out along the direction the volley was
 * aimed and bursts into an area-of-effect hit on the first thing it touches - a valid target, a
 * wall, or the end of its range.
 *
 * Carries the volley's refund token so that a kill can be reported back to
 * {@link ShadowSoulManager}, which is what gives a full-power volley its souls back.
 */
public class ShadowSoulEntity extends Entity {

    private static final double SPEED = 1.0;
    private static final double MAX_RANGE = 24.0;
    private static final double HIT_RADIUS = 0.6;
    private static final double AOE_RADIUS = 3.0;
    private static final float AOE_DAMAGE = 10.0F;

    private UUID ownerUUID;
    private int refundToken = ShadowSoulManager.NO_REFUND_TOKEN;
    private double distanceTraveled = 0.0;

    public ShadowSoulEntity(EntityType<? extends ShadowSoulEntity> type, Level level) {
        super(type, level);
    }

    public ShadowSoulEntity(Level level, Player owner, Vec3 direction, int refundToken) {
        this(ModEntityTypes.SHADOW_SOUL.get(), level);
        this.ownerUUID = owner.getUUID();
        this.refundToken = refundToken;

        Vec3 aim = direction.normalize();
        Vec3 spawn = owner.getEyePosition().add(aim.scale(0.8)).subtract(0.0, 0.2, 0.0);
        this.setPos(spawn.x, spawn.y, spawn.z);
        this.setDeltaMovement(aim.scale(SPEED));
    }

    @Override
    protected void defineSynchedData() {
        // Nothing to sync - the client only ever draws the particle trail below, and position
        // already rides along on the standard entity tracking.
    }

    @Override
    public void tick() {
        super.tick();

        if (level().isClientSide) {
            level().addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                    getX() + (random.nextDouble() - 0.5) * 0.3,
                    getY() + (random.nextDouble() - 0.5) * 0.3,
                    getZ() + (random.nextDouble() - 0.5) * 0.3,
                    0, 0, 0);
            level().addParticle(ParticleTypes.SMOKE, getX(), getY(), getZ(), 0, 0, 0);
            return;
        }

        Vec3 movement = getDeltaMovement();
        this.move(MoverType.SELF, movement);
        distanceTraveled += movement.length();

        List<LivingEntity> hits = level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(HIT_RADIUS),
                e -> e.isAlive() && !e.getUUID().equals(ownerUUID) && FireKatanaItem.isValidFireTarget(e));

        if (!hits.isEmpty() || this.horizontalCollision || this.verticalCollision || distanceTraveled >= MAX_RANGE) {
            burst();
        }
    }

    /** The soul's payload: everything nearby takes a hit, and a kill buys the volley a refund. */
    private void burst() {
        Player owner = ownerUUID == null ? null : level().getPlayerByUUID(ownerUUID);
        DamageSource source = owner != null
                ? level().damageSources().indirectMagic(this, owner)
                : level().damageSources().magic();

        List<LivingEntity> targets = level().getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(AOE_RADIUS),
                e -> e.isAlive() && !e.getUUID().equals(ownerUUID) && FireKatanaItem.isValidFireTarget(e));

        boolean killedSomething = false;
        for (LivingEntity target : targets) {
            // Souls in a volley land 2-4 ticks apart, well inside vanilla's 20-tick invulnerability
            // window, so without clearing it only the first soul of a volley would ever register.
            int previousInvulnerableTime = target.invulnerableTime;
            target.invulnerableTime = 0;
            target.hurt(source, AOE_DAMAGE);
            target.invulnerableTime = previousInvulnerableTime;

            if (!target.isAlive() || target.getHealth() <= 0.0F) {
                killedSomething = true;
            }
        }

        if (killedSomething) {
            ShadowSoulManager.reportSoulKill(refundToken, owner);
        }

        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY(), getZ(),
                    30, AOE_RADIUS / 3.0, AOE_RADIUS / 3.0, AOE_RADIUS / 3.0, 0.05);
            serverLevel.sendParticles(ParticleTypes.SOUL, getX(), getY(), getZ(),
                    12, 0.5, 0.5, 0.5, 0.02);
            serverLevel.playSound(null, getX(), getY(), getZ(),
                    SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1.0F, 0.6F);
        }

        discard();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) this.ownerUUID = tag.getUUID("Owner");
        this.refundToken = tag.getInt("RefundToken");
        this.distanceTraveled = tag.getDouble("DistanceTraveled");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerUUID != null) tag.putUUID("Owner", ownerUUID);
        tag.putInt("RefundToken", refundToken);
        tag.putDouble("DistanceTraveled", distanceTraveled);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return true;
    }
}
