package net.finnigan.tommemod.entity.custom.LumapierHelpers;

import net.finnigan.tommemod.entity.ModEntityTypes;
import net.finnigan.tommemod.item.ModItems;
import net.finnigan.tommemod.item.custom.LumapierItem;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Lumapier's charged shot: an enormous, fast, perfectly straight rod of light.
 */
public class LightBoltProjectileEntity extends ThrowableItemProjectile implements GeoEntity {

    private static final float DAMAGE = 40.0F;
    private static final double CONTACT_RADIUS = 0.85D;
    private static final double HOMING_STRENGTH = 0.075D;
    private static final int IMPACT_SMOKE_TICKS = 200;
    private static final int MAX_ACTIVE_RODS_PER_OWNER = 8;
    private static final EntityDataAccessor<Boolean> LAUNCHED =
            SynchedEntityData.defineId(LightBoltProjectileEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> STUCK =
            SynchedEntityData.defineId(LightBoltProjectileEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> OWNER_ID =
            SynchedEntityData.defineId(LightBoltProjectileEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> IMPACT_YAW =
            SynchedEntityData.defineId(LightBoltProjectileEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> IMPACT_PITCH =
            SynchedEntityData.defineId(LightBoltProjectileEntity.class, EntityDataSerializers.FLOAT);
    // The supplied animation declares hold_on_last_frame. Play it once so the fully extended rod
    // remains overhead after the two-second charge rather than restarting its growth cycle.
    private static final RawAnimation ROD_ANIMATION = RawAnimation.begin().thenPlay("animation");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private LivingEntity lumapierOwner;
    private LivingEntity homingTarget;
    private int stuckSmokeTicks;

    public LightBoltProjectileEntity(EntityType<? extends LightBoltProjectileEntity> type, Level level) {
        super(type, level);
    }

    public LightBoltProjectileEntity(Level level, LivingEntity owner) {
        super(ModEntityTypes.LIGHT_BOLT_PROJECTILE.get(), owner, level);
        this.setItem(new ItemStack(ModItems.LUMAPIER.get()));
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.LUMAPIER.get();
    }

    @Override
    protected float getGravity() {
        return 0.0F;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(LAUNCHED, false);
        this.entityData.define(STUCK, false);
        this.entityData.define(OWNER_ID, -1);
        this.entityData.define(IMPACT_YAW, 0.0F);
        this.entityData.define(IMPACT_PITCH, 0.0F);
    }

    public void setLumapierOwner(LivingEntity owner) {
        super.setOwner(owner);
        this.lumapierOwner = owner;
        this.entityData.set(OWNER_ID, owner.getId());
    }

    public boolean isLaunched() {
        return this.entityData.get(LAUNCHED);
    }

    /** Returns the hovering rod's owner on both sides once the synced owner id is available. */
    public LivingEntity getLumapierOwner() {
        if (lumapierOwner == null && this.entityData.get(OWNER_ID) != -1
                && this.level().getEntity(this.entityData.get(OWNER_ID)) instanceof LivingEntity owner) {
            lumapierOwner = owner;
        }
        return lumapierOwner;
    }

    /** Changes the fully charged hovering rod into a normal fast projectile. */
    public void launch(LivingEntity owner) {
        setLumapierOwner(owner);
        this.entityData.set(LAUNCHED, true);
        this.entityData.set(STUCK, false);
        // Because the rod begins above the player, a parallel look vector misses nearby crosshair
        // targets. Raycast from the eye first, then aim the elevated rod at that exact target.
        Vec3 eye = owner.getEyePosition();
        Vec3 rayEnd = eye.add(owner.getViewVector(1.0F).scale(128.0D));
        BlockHitResult blockHit = level().clip(new ClipContext(eye, rayEnd, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, owner));
        Vec3 target = blockHit.getType() == HitResult.Type.MISS ? rayEnd : blockHit.getLocation();
        Vec3 aimEnd = target;
        LivingEntity nearCrosshair = level().getEntities(owner, new AABB(eye, aimEnd).inflate(1.0D),
                        entity -> entity instanceof LivingEntity && entity.isPickable() && entity != owner)
                .stream()
                .filter(entity -> entity.getBoundingBox().inflate(1.0D).clip(eye, aimEnd).isPresent())
                .map(entity -> (LivingEntity) entity)
                .min((first, second) -> Double.compare(eye.distanceToSqr(first.getBoundingBox().getCenter()),
                        eye.distanceToSqr(second.getBoundingBox().getCenter())))
                .orElse(null);
        if (nearCrosshair != null) {
            target = nearCrosshair.getBoundingBox().getCenter();
            this.homingTarget = nearCrosshair;
        }
        Vec3 direction = target.subtract(this.position()).normalize();
        this.setDeltaMovement(direction.scale(3.5D));
        setRodRotation(direction);
        enforceRodLimit(owner);
    }

    /** Keeps only the eight newest launched rods belonging to this wielder in the current level. */
    private void enforceRodLimit(LivingEntity owner) {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;

        List<LightBoltProjectileEntity> ownedRods = new ArrayList<>();
        for (Entity entity : serverLevel.getAllEntities()) {
            if (entity instanceof LightBoltProjectileEntity rod && rod.isLaunched()
                    && rod.getOwner() == owner) {
                ownedRods.add(rod);
            }
        }
        ownedRods.sort(Comparator.comparingInt((LightBoltProjectileEntity rod) -> rod.tickCount).reversed());
        for (int index = 0; index < ownedRods.size() - MAX_ACTIVE_RODS_PER_OWNER; index++) {
            ownedRods.get(index).discard();
        }
    }

    @Override
    public void tick() {
        if (isLaunched() && !this.level().isClientSide && isBeyondOwnerRenderDistance()) {
            this.discard();
            return;
        }
        if (this.entityData.get(STUCK)) {
            this.baseTick();
            if (!this.level().isClientSide && stuckSmokeTicks < IMPACT_SMOKE_TICKS) {
                spawnImpactSmokeParticles();
                stuckSmokeTicks++;
            }
            return;
        }
        if (!isLaunched()) {
            this.baseTick(); // advances the two-second GeckoLib grow animation without projectile collision.
            getLumapierOwner();
            if (lumapierOwner == null || !lumapierOwner.isAlive()
                    || (!this.level().isClientSide && !LumapierItem.isActivelyChargingRod(lumapierOwner, this))) {
                this.discard();
                return;
            }
            this.setPos(lumapierOwner.getX(), lumapierOwner.getEyeY() + 5.0D, lumapierOwner.getZ());
            this.setYRot(lumapierOwner.getYRot());
            this.setXRot(lumapierOwner.getXRot());
            return;
        }

        if (!this.level().isClientSide) {
            applyGentleHoming();
            spawnFlightSmokeParticles();

            Vec3 movement = this.getDeltaMovement();
            AABB contactArea = this.getBoundingBox().expandTowards(movement).inflate(CONTACT_RADIUS);
            Entity contact = this.level().getEntities(this, contactArea, this::canDamageOnContact).stream()
                    .min((first, second) -> Double.compare(this.distanceToSqr(first), this.distanceToSqr(second)))
                    .orElse(null);
            if (contact != null) {
                onHitEntity(new EntityHitResult(contact));
                return;
            }
        }
        super.tick();
    }

    /** Removes rods once they move outside the owning player's server-side view radius. */
    private boolean isBeyondOwnerRenderDistance() {
        if (!(this.getOwner() instanceof ServerPlayer owner) || !(this.level() instanceof ServerLevel serverLevel)) {
            return true;
        }
        double renderDistance = serverLevel.getServer().getPlayerList().getViewDistance() * 16.0D;
        double deltaX = this.getX() - owner.getX();
        double deltaZ = this.getZ() - owner.getZ();
        return deltaX * deltaX + deltaZ * deltaZ > renderDistance * renderDistance;
    }

    /** Curves only slightly toward the entity that was within one block of the crosshair at launch. */
    private void applyGentleHoming() {
        if (homingTarget == null || !homingTarget.isAlive() || homingTarget == getOwner()) return;

        Vec3 velocity = this.getDeltaMovement();
        Vec3 towardTarget = homingTarget.getBoundingBox().getCenter().subtract(this.position());
        if (velocity.lengthSqr() < 1.0E-6D || towardTarget.lengthSqr() < 1.0E-6D) return;

        double speed = velocity.length();
        Vec3 adjusted = velocity.normalize().scale(1.0D - HOMING_STRENGTH)
                .add(towardTarget.normalize().scale(HOMING_STRENGTH))
                .normalize().scale(speed);
        this.setDeltaMovement(adjusted);
        setRodRotation(adjusted.normalize());
    }

    /** Pale smoke fills the complete centered rod while it is airborne. */
    private void spawnFlightSmokeParticles() {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;

        Vec3 center = this.position();
        Vec3 direction = getSmokeDirection();
        spawnInsideRod(serverLevel, ParticleTypes.CLOUD, center, direction, 52, 0.008D);
        spawnInsideRod(serverLevel, ParticleTypes.POOF, center, direction, 13, 0.004D);
    }

    /** Pale smoke fills a lodged rod for its ten-second particle lifetime. */
    private void spawnImpactSmokeParticles() {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;

        Vec3 center = this.position();
        Vec3 direction = getSmokeDirection();
        // Hold the heavy gray smoke briefly, then finish the pale-cloud crossfade at two seconds.
        float transition = Mth.clamp((stuckSmokeTicks - 30) / 10.0F, 0.0F, 1.0F);
        int cloudCount = 1 + Math.round(5.0F * transition);

        // Sample continuously across the full length so no fixed one-block emission blobs appear.
        spawnInsideRod(serverLevel, ParticleTypes.CLOUD, center, direction, cloudCount * 13, 0.005D);
        if (transition >= 1.0F) {
            spawnInsideRod(serverLevel, ParticleTypes.POOF, center, direction, 13, 0.003D);
        }
    }

    /** Places particles continuously throughout the rod's rotated twelve-block volume. */
    private void spawnInsideRod(ServerLevel level, ParticleOptions particle, Vec3 center,
                                Vec3 direction, int count, double speed) {
        Vec3 across = direction.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (across.lengthSqr() < 1.0E-6D) {
            across = direction.cross(new Vec3(1.0D, 0.0D, 0.0D));
        }
        across = across.normalize();
        Vec3 vertical = direction.cross(across).normalize();

        for (int index = 0; index < count; index++) {
            double lengthOffset = (level.getRandom().nextDouble() - 0.5D) * 12.0D;
            double acrossOffset = (level.getRandom().nextDouble() - 0.5D) * 0.46D;
            double verticalOffset = (level.getRandom().nextDouble() - 0.5D) * 0.46D;
            Vec3 position = center.add(direction.scale(lengthOffset))
                    .add(across.scale(acrossOffset)).add(vertical.scale(verticalOffset));
            // Force the packet's long-distance flag so smoke remains visible wherever the rod is tracked.
            for (ServerPlayer player : level.players()) {
                level.sendParticles(player, particle, true, position.x, position.y, position.z,
                        1, 0.015D, 0.015D, 0.015D, speed);
            }
        }
    }

    private Vec3 getSmokeDirection() {
        Vec3 movement = this.getDeltaMovement();
        return movement.lengthSqr() > 1.0E-6D
                ? movement.normalize()
                : Vec3.directionFromRotation(this.getImpactPitch(), this.getImpactYaw());
    }

    private boolean canDamageOnContact(Entity entity) {
        return entity != this.getOwner() && entity != getLumapierOwner()
                && entity.isAlive() && entity.isPickable() && !entity.isSpectator();
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return canDamageOnContact(entity) && super.canHitEntity(entity);
    }

    public boolean isStuck() {
        return this.entityData.get(STUCK);
    }

    public float getImpactYaw() {
        return this.entityData.get(IMPACT_YAW);
    }

    public float getImpactPitch() {
        return this.entityData.get(IMPACT_PITCH);
    }

    @Override
    public net.minecraft.world.phys.AABB getBoundingBoxForCulling() {
        return this.getBoundingBox().inflate(36.0D);
    }

    private PlayState animationPredicate(software.bernie.geckolib.core.animation.AnimationState<LightBoltProjectileEntity> state) {
        state.getController().setAnimation(ROD_ANIMATION);
        return PlayState.CONTINUE;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "lumapierRodController", 0, this::animationPredicate));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }


    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (this.level().isClientSide) return;

        Entity target = result.getEntity();
        Entity owner = this.getOwner();
        DamageSource source = this.damageSources().thrown(this, owner != null ? owner : this);
        target.hurt(source, DAMAGE);
        stick(result.getLocation());
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (this.level().isClientSide) return;

        stick(result.getLocation());
    }

    /** Leaves the rod lodged at its impact point, matching the persistence of an arrow. */
    private void stick(net.minecraft.world.phys.Vec3 location) {
        // Preserve the flight direction before zeroing the motion. Without this, the renderer falls
        // back to ThrowableProjectile's post-collision rotation and the long model appears sideways.
        Vec3 direction = this.getDeltaMovement();
        if (direction.lengthSqr() > 1.0E-6D) {
            setRodRotation(direction.normalize());
        }
        this.setPos(location.x, location.y, location.z);
        this.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        this.hasImpulse = true;
        this.entityData.set(STUCK, true);
        this.stuckSmokeTicks = 0;
        this.level().playSound(null, location.x, location.y, location.z, SoundEvents.BELL_BLOCK,
                SoundSource.PLAYERS, 64.0F, 0.7F);
    }

    /** Stores a render-stable orientation before the projectile's collision code can alter it. */
    private void setRodRotation(Vec3 direction) {
        float yaw = (float) (Mth.atan2(-direction.x, direction.z) * Mth.RAD_TO_DEG);
        float pitch = (float) (-Math.asin(direction.y) * Mth.RAD_TO_DEG);
        this.setYRot(yaw);
        this.setXRot(pitch);
        this.yRotO = yaw;
        this.xRotO = pitch;
        this.entityData.set(IMPACT_YAW, yaw);
        this.entityData.set(IMPACT_PITCH, pitch);
    }
}
