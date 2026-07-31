package net.finnigan.tommemod.entity.custom.EndScytheHelpers;

import net.finnigan.tommemod.entity.ModEntityTypes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * End Scythe's projectile: spinning, fast, passes through walls (noPhysics), homes toward the nearest
 * player (excluding the shooter) with a gradual steering blend rather than an instant re-aim snap.
 * Travels a maximum of 32 blocks (tracked manually since it has no real Projectile collision machinery)
 * and deals 16 damage on proximity contact with a player.
 */
public class EndScytheProjectileEntity extends Entity {

    private static final double SPEED = 1.8;
    private static final double STEER_STRENGTH = 0.15; // per-tick blend factor toward the target direction
    private static final double MAX_RANGE = 32.0;
    private static final float DAMAGE = 16.0F;
    private static final double HIT_RADIUS = 0.4;

    private UUID ownerUUID;
    private double distanceTraveled = 0.0;

    public EndScytheProjectileEntity(EntityType<? extends EndScytheProjectileEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public EndScytheProjectileEntity(Level level, Player owner) {
        this(ModEntityTypes.END_SCYTHE_PROJECTILE.get(), level);
        this.ownerUUID = owner.getUUID();
        Vec3 eye = owner.getEyePosition();
        this.setPos(eye.x, eye.y - 0.1, eye.z);
        Vec3 look = owner.getLookAngle().normalize();
        this.setDeltaMovement(look.scale(SPEED));
        this.setYRot((float) (Math.atan2(look.x, look.z) * (180F / Math.PI)));
    }

    @Override
    protected void defineSynchedData() {
        // no synced state needed; position/rotation sync is handled automatically, spin is derived
        // client-side from tickCount (see EndScytheProjectileRenderer).
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

        Player target = findNearestTarget();
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

        List<Player> hits = level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(HIT_RADIUS),
                p -> p.isAlive() && !p.isSpectator() && !p.getUUID().equals(ownerUUID));

        if (!hits.isEmpty()) {
            for (Player p : hits) {
                p.hurt(level().damageSources().magic(), DAMAGE);
            }
            discard();
            return;
        }

        if (distanceTraveled >= MAX_RANGE) {
            discard();
        }
    }

    /** Nearest living, non-spectator player in the level, excluding the shooter. */
    private Player findNearestTarget() {
        Player nearest = null;
        double nearestDistSq = Double.MAX_VALUE;

        for (Player p : level().players()) {
            if (p.getUUID().equals(ownerUUID)) continue;
            if (!p.isAlive() || p.isSpectator()) continue;

            double distSq = p.position().distanceToSqr(this.position());
            if (distSq < nearestDistSq) {
                nearestDistSq = distSq;
                nearest = p;
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
