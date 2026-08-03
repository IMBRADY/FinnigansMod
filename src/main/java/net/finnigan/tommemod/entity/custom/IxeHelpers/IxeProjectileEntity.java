package net.finnigan.tommemod.entity.custom.IxeHelpers;

import net.finnigan.tommemod.effect.ModMobEffects;
import net.finnigan.tommemod.entity.ModEntityTypes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/** Ixe's spinning AOE projectile: flies forward, bursts into a freezing AOE on impact or max range. */
public class IxeProjectileEntity extends Entity {

    private static final double SPEED = 0.9;
    private static final double MAX_RANGE = 24.0;
    private static final double BURST_RADIUS = 3.0;
    private static final int FROZEN_DURATION_TICKS = 40; // 2 seconds

    // Synced purely so the client can find the shooter's head: the projectile spins around the axis
    // running from there to itself (see IxeProjectileRenderer), which the server never needs.
    private static final EntityDataAccessor<Integer> OWNER_ID =
            SynchedEntityData.defineId(IxeProjectileEntity.class, EntityDataSerializers.INT);

    private UUID ownerUUID;
    private Vec3 spawnPos = Vec3.ZERO;

    public IxeProjectileEntity(EntityType<? extends IxeProjectileEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public IxeProjectileEntity(Level level, Player owner) {
        this(ModEntityTypes.IXE_PROJECTILE.get(), level);
        this.ownerUUID = owner.getUUID();
        this.entityData.set(OWNER_ID, owner.getId());
        Vec3 eye = owner.getEyePosition();
        this.setPos(eye.x, eye.y - 0.1, eye.z);
        this.spawnPos = this.position();
        Vec3 look = owner.getLookAngle().normalize();
        this.setDeltaMovement(look.scale(SPEED));
        this.setYRot((float) (Math.atan2(look.x, look.z) * (180F / Math.PI)));
    }

    @Override
    protected void defineSynchedData() {
        // Movement/particles are simulated client-side from the server-authoritative position; only
        // the shooter's id has to be carried across (see OWNER_ID).
        this.entityData.define(OWNER_ID, -1);
    }

    /** The shooter as the client knows them, or null if they aren't loaded/tracked there. */
    public Entity getOwnerEntity() {
        int id = this.entityData.get(OWNER_ID);
        return id == -1 ? null : level().getEntity(id);
    }

    @Override
    public void tick() {
        super.tick();

        if (level().isClientSide) {
            level().addParticle(ParticleTypes.SNOWFLAKE,
                    getX() + (random.nextDouble() - 0.5) * 0.3,
                    getY() + (random.nextDouble() - 0.5) * 0.3,
                    getZ() + (random.nextDouble() - 0.5) * 0.3,
                    0, 0, 0);
            return;
        }

        Vec3 movement = getDeltaMovement();
        this.move(MoverType.SELF, movement);

        boolean hitBlock = !level().noCollision(this, getBoundingBox());
        boolean hitEntity = !level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.3),
                e -> e.isAlive() && !e.getUUID().equals(ownerUUID)).isEmpty();
        boolean pastRange = position().distanceTo(spawnPos) >= MAX_RANGE;

        if (hitBlock || hitEntity || pastRange) {
            burst((ServerLevel) level());
            discard();
        }
    }

    private void burst(ServerLevel level) {
        level.sendParticles(ParticleTypes.SNOWFLAKE, getX(), getY(), getZ(), 40,
                BURST_RADIUS * 0.4, BURST_RADIUS * 0.4, BURST_RADIUS * 0.4, 0.02);
        level.sendParticles(ParticleTypes.ITEM_SNOWBALL, getX(), getY(), getZ(), 20,
                BURST_RADIUS * 0.3, BURST_RADIUS * 0.3, BURST_RADIUS * 0.3, 0.05);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.2F, 0.7F);

        AABB burstBox = new AABB(getX() - BURST_RADIUS, getY() - BURST_RADIUS, getZ() - BURST_RADIUS,
                getX() + BURST_RADIUS, getY() + BURST_RADIUS, getZ() + BURST_RADIUS);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, burstBox,
                e -> e.isAlive() && !e.getUUID().equals(ownerUUID));

        for (LivingEntity target : targets) {
            if (target.distanceTo(this) > BURST_RADIUS) continue;
            target.addEffect(new MobEffectInstance(ModMobEffects.FROZEN.get(), FROZEN_DURATION_TICKS, 0));

            IxeBoxEntity box = new IxeBoxEntity(ModEntityTypes.IXE_BOX.get(), level);
            box.followTarget(target, FROZEN_DURATION_TICKS);
            box.setPos(target.getX(), target.getY(), target.getZ());
            level.addFreshEntity(box);
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) this.ownerUUID = tag.getUUID("Owner");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerUUID != null) tag.putUUID("Owner", ownerUUID);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return true;
    }
}
