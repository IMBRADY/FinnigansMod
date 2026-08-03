package net.finnigan.tommemod.entity.custom.EndScytheHelpers;

import net.finnigan.tommemod.entity.ModEntityTypes;
import net.finnigan.tommemod.item.custom.FireKatanaItem;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
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
 * End Scythe's projectile: spinning, fast, passes through walls (noPhysics), homes toward the nearest
 * valid enemy (excluding the shooter, reusing FireKatanaItem's "valid target" filter - excludes other
 * players/wolves/villagers/soul-allies, so it actually finds hostile mobs in singleplayer instead of
 * only ever looking for another Player) with a gradual steering blend rather than an instant re-aim
 * snap. Travels a maximum of 32 blocks (tracked manually since it has no real Projectile collision
 * machinery) and deals 16 damage on proximity contact with a valid target.
 */
public class EndScytheProjectileEntity extends Entity {

    private static final double SPEED = 1.8;
    private static final double STEER_STRENGTH = 0.15; // per-tick blend factor toward the target direction
    private static final double MAX_RANGE = 128.0;
    private static final float DAMAGE = 16.0F;
    private static final double HIT_RADIUS = 0.6;

    // Synced purely so the client can find the shooter's head: the projectile spins around the axis
    // running from there to itself (see EndScytheProjectileRenderer), which the server never needs.
    private static final EntityDataAccessor<Integer> OWNER_ID =
            SynchedEntityData.defineId(EndScytheProjectileEntity.class, EntityDataSerializers.INT);

    private UUID ownerUUID;
    private double distanceTraveled = 0.0;

    public EndScytheProjectileEntity(EntityType<? extends EndScytheProjectileEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public EndScytheProjectileEntity(Level level, Player owner) {
        this(ModEntityTypes.END_SCYTHE_PROJECTILE.get(), level);
        this.ownerUUID = owner.getUUID();
        this.entityData.set(OWNER_ID, owner.getId());
        Vec3 eye = owner.getEyePosition();
        this.setPos(eye.x, eye.y - 0.1, eye.z);
        Vec3 look = owner.getLookAngle().normalize();
        this.setDeltaMovement(look.scale(SPEED));
        this.setYRot((float) (Math.atan2(look.x, look.z) * (180F / Math.PI)));
    }

    @Override
    protected void defineSynchedData() {
        // Position/rotation sync is handled automatically and the spin angle is derived client-side
        // from tickCount; only the shooter's id has to be carried across (see OWNER_ID).
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
            level().addParticle(ParticleTypes.REVERSE_PORTAL,
                    getX() + (random.nextDouble() - 0.5) * 0.3,
                    getY() + (random.nextDouble() - 0.5) * 0.3,
                    getZ() + (random.nextDouble() - 0.5) * 0.3,
                    0, 0, 0);
            return;
        }

        LivingEntity target = findNearestTarget();
        if (target != null) {
            Vec3 desiredDir = target.position().add(0, target.getBbHeight() / 2, 0)
                    .subtract(this.position())
                    .normalize();
            Vec3 currentDir = getDeltaMovement().lengthSqr() > 1.0E-6
                    ? getDeltaMovement().normalize()
                    : desiredDir;
            Vec3 blendedDir = currentDir.scale(1.0 - STEER_STRENGTH).add(desiredDir.scale(STEER_STRENGTH)).normalize();
            setDeltaMovement(blendedDir.scale(SPEED));
        }

        Vec3 movement = getDeltaMovement();
        this.move(MoverType.SELF, movement);
        distanceTraveled += movement.length();

        if (movement.lengthSqr() > 1.0E-6) {
            this.setYRot((float) (Math.atan2(movement.x, movement.z) * (180F / Math.PI)));
        }

        List<LivingEntity> hits = level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(HIT_RADIUS),
                e -> e.isAlive() && !e.getUUID().equals(ownerUUID) && FireKatanaItem.isValidFireTarget(e));

        if (!hits.isEmpty()) {
            for (LivingEntity e : hits) {
                e.hurt(level().damageSources().magic(), DAMAGE);
            }
            discard();
            return;
        }

        if (distanceTraveled >= MAX_RANGE) {
            discard();
        }
    }

    /** Nearest valid enemy (see class doc for the exclusion filter), excluding the shooter. Scans a
     * generous radius each tick since (unlike a real Projectile) this entity has no target-tracking
     * machinery of its own to piggyback on. */
    private LivingEntity findNearestTarget() {
        double searchRadius = MAX_RANGE;
        List<LivingEntity> candidates = level().getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(searchRadius),
                e -> e.isAlive() && !e.getUUID().equals(ownerUUID) && FireKatanaItem.isValidFireTarget(e));

        LivingEntity nearest = null;
        double nearestDistSq = Double.MAX_VALUE;
        for (LivingEntity e : candidates) {
            double distSq = e.position().distanceToSqr(this.position());
            if (distSq < nearestDistSq) {
                nearestDistSq = distSq;
                nearest = e;
            }
        }

        return nearest;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) this.ownerUUID = tag.getUUID("Owner");
        this.distanceTraveled = tag.getDouble("DistanceTraveled");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerUUID != null) tag.putUUID("Owner", ownerUUID);
        tag.putDouble("DistanceTraveled", distanceTraveled);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return true;
    }
}
