package net.finnigan.tommemod.entity.custom;

import net.finnigan.tommemod.entity.ModEntityTypes;
import net.finnigan.tommemod.item.ModItems;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class MusicNoteEntity extends ThrowableItemProjectile {

    private double distanceTraveled = 0;
    private static final double MAX_STRAIGHT_DISTANCE = 10.0;

    private LivingEntity target;
    private Vec3 initialLookDir = null;

    public MusicNoteEntity(EntityType<? extends ThrowableItemProjectile> type, Level level) {
        super(type, level);
    }

    public MusicNoteEntity(Level level, Player shooter) {
        this(ModEntityTypes.MUSIC_NOTE.get(), level);
        this.setOwner(shooter);
    }

    public void setInitialLookDir(Vec3 lookDir) {
        this.initialLookDir = lookDir;
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.MUSIC_NOTE_ITEM.get();
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    public void tick() {
        // REMOVED — the entire "if (spawnDelay > 0) { ... }" block is gone

        distanceTraveled += getDeltaMovement().length();

        if (target == null || !target.isAlive()) {
            target = findEarlyTarget();
            if (target == null && distanceTraveled >= MAX_STRAIGHT_DISTANCE) {
                target = findTarget();
            }
        }

        if (target != null) {
            Vec3 dir = target.position().add(0, target.getBbHeight() / 2, 0)
                    .subtract(this.position())
                    .normalize();
            setDeltaMovement(dir.scale(0.35));
        } else {
            Vec3 dir = (initialLookDir != null) ? initialLookDir : getDeltaMovement().normalize();
            setDeltaMovement(dir.scale(0.40));
        }

        super.tick();

        for (Entity e : level().getEntities(this, getBoundingBox().inflate(0.2))) {
            if (e instanceof LivingEntity living && e != getOwner() && isValidTarget(living)) {
                living.hurt(level().damageSources().magic(), 4.0F);
                level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.NOTE_BLOCK_BELL.get(), SoundSource.NEUTRAL, 1.0F, 1.0F);
                discard();
                break;
            }
        }

        if (tickCount > 90) discard(); // CHANGED — no longer needs "+ spawnDelay" since delay is gone
    }

private LivingEntity findEarlyTarget() {
    double searchRadius = Math.max(distanceTraveled, 1.0);
    AABB box = getBoundingBox().inflate(searchRadius);

    List<LivingEntity> candidates = level().getEntitiesOfClass(
            LivingEntity.class, box, e -> e != getOwner() && e.isAlive() && isValidTarget(e)
    );

    LivingEntity closest = null;
    double closestDist = searchRadius; // must be within (i.e. "closer than") distance traveled

    for (LivingEntity e : candidates) {
        double dist = this.distanceTo(e);
        if (dist < closestDist) {
            closestDist = dist;
            closest = e;
        }
    }

    return closest;
}

    /**
     * Picks the entity most aligned with the player's original look direction (crosshair-based),
     * rather than simply the closest one.
     */
    private LivingEntity findTarget() {
        Entity owner = getOwner();
        if (!(owner instanceof Player player)) return null;

        double range = 16.0;
        Vec3 lookDir = (initialLookDir != null) ? initialLookDir : player.getLookAngle();
        Vec3 eyePos = player.getEyePosition();

        AABB box = player.getBoundingBox().inflate(range);
        List<LivingEntity> candidates = level().getEntitiesOfClass(
                LivingEntity.class, box, e -> e != owner && e.isAlive() && isValidTarget(e)
        );

        LivingEntity best = null;
        double bestAngle = Math.toRadians(35); // max cone angle allowed (adjust to taste)

        for (LivingEntity e : candidates) {
            Vec3 toEntity = e.position().add(0, e.getBbHeight() / 2, 0).subtract(eyePos).normalize();
            double angle = Math.acos(lookDir.dot(toEntity));
            if (angle < bestAngle) {
                bestAngle = angle;
                best = e;
            }
        }

        return best;
    }

    /**
     * Filters out entities that should never be targeted/damaged.
     */
    private boolean isValidTarget(LivingEntity entity) {
        if (entity instanceof Player) return false;
        if (entity instanceof Villager) return false;
        if (entity instanceof Wolf) return false;
        return true;
    }
}