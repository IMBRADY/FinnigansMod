package net.finnigan.tommemod.entity.custom;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.UUID;

public class GiantSwordEntity extends Entity {

    private static final double FALL_SPEED = 2.5; // blocks/tick
    private static final double IMPACT_RADIUS = 7.0;
    private static final float IMPACT_DAMAGE = 100.0F;

    private static final EntityDataAccessor<Boolean> HAS_IMPACTED =
            SynchedEntityData.defineId(GiantSwordEntity.class, EntityDataSerializers.BOOLEAN);

    private UUID ownerUUID;
    private int postImpactTicks = 0;

    public GiantSwordEntity(EntityType<? extends GiantSwordEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public void setOwner(Player player) {
        this.ownerUUID = player.getUUID();
    }

    private static final EntityDataAccessor<Float> SPIN_SPEED =
            SynchedEntityData.defineId(GiantSwordEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SPIN_AXIS_YAW =
            SynchedEntityData.defineId(GiantSwordEntity.class, EntityDataSerializers.FLOAT);

    @Override
    protected void defineSynchedData() {
        this.entityData.define(HAS_IMPACTED, false);
        this.entityData.define(SPIN_SPEED, 0F);
        this.entityData.define(SPIN_AXIS_YAW, 0F);
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.entityData.get(HAS_IMPACTED)) {
            this.setDeltaMovement(0, -FALL_SPEED, 0);
            this.move(net.minecraft.world.entity.MoverType.SELF, this.getDeltaMovement());

            if (!level().isClientSide) {
                // Check block collision below to know when to "land"
                AABB checkBox = this.getBoundingBox().move(0, -0.5, 0);
                boolean blocked = !level().noCollision(this, checkBox);

                if (blocked || this.getY() <= level().getMinBuildHeight()) {
                    onImpact();
                }
            }
        } else {
            postImpactTicks++;
            if (postImpactTicks > 30) { // linger time
                discard();
            }
        }
    }

    private void onImpact() {
        this.entityData.set(HAS_IMPACTED, true);
        this.setDeltaMovement(Vec3ZERO());

        level().playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.TRIDENT_RETURN, SoundSource.PLAYERS, 4.0F, 0.8F);

        if (level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.EXPLOSION,
                    this.getX(), this.getY(), this.getZ(),
                    8,
                    IMPACT_RADIUS * 0.2, 1.0, IMPACT_RADIUS * 0.2,
                    0.0);
        }

        AABB damageArea = new AABB(
                this.getX() - IMPACT_RADIUS, this.getY() - 2, this.getZ() - IMPACT_RADIUS,
                this.getX() + IMPACT_RADIUS, this.getY() + 4, this.getZ() + IMPACT_RADIUS);

        List<LivingEntity> targets = level().getEntitiesOfClass(LivingEntity.class, damageArea,
                e -> e.isAlive() && !e.getUUID().equals(ownerUUID));

        for (LivingEntity target : targets) {
            double distance = target.position().distanceTo(this.position());
            float falloff = (float) Math.max(0.2, 1.0 - (distance / IMPACT_RADIUS));

            target.hurt(level().damageSources().generic(), IMPACT_DAMAGE * falloff);
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));

            Vec3ZERO(); // no-op placeholder to keep diff minimal; remove if unused
            double dx = target.getX() - this.getX();
            double dz = target.getZ() - this.getZ();
            target.knockback(1.2 * falloff, -dx, -dz);
        }
    }

    private net.minecraft.world.phys.Vec3 Vec3ZERO() {
        return net.minecraft.world.phys.Vec3.ZERO;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) this.ownerUUID = tag.getUUID("Owner");
        this.entityData.set(HAS_IMPACTED, tag.getBoolean("HasImpacted"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerUUID != null) tag.putUUID("Owner", ownerUUID);
        tag.putBoolean("HasImpacted", this.entityData.get(HAS_IMPACTED));
    }

    public boolean hasImpacted() {
        return this.entityData.get(HAS_IMPACTED);
    }
}