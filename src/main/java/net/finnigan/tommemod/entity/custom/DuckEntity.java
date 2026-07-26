package net.finnigan.tommemod.entity.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;
import java.util.List;

public class DuckEntity extends Animal implements GeoEntity {

    private static final double THREAT_RADIUS = 5.0;
    private static final RawAnimation FLY_ANIM = RawAnimation.begin().thenLoop("fly");
    private static final RawAnimation EAT_ANIM = RawAnimation.begin().thenPlay("eat");
    private static final RawAnimation SWIM_ANIM = RawAnimation.begin().thenLoop("swim");
    private static final RawAnimation WALK_ANIM = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("idle");

    private static final EntityDataAccessor<Boolean> DATA_FLYING =
            SynchedEntityData.defineId(DuckEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_EATING =
            SynchedEntityData.defineId(DuckEntity.class, EntityDataSerializers.BOOLEAN);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private boolean recentlyHurt = false;
    private int eatTimer = 0;

    public DuckEntity(EntityType<? extends Animal> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 4.0)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.FLYING_SPEED, 0.6);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_FLYING, false);
        this.entityData.define(DATA_EATING, false);
    }

    public boolean isFlying() {
        return this.entityData.get(DATA_FLYING);
    }

    public void setFlying(boolean flying) {
        this.entityData.set(DATA_FLYING, flying);
    }

    public boolean isEating() {
        return this.entityData.get(DATA_EATING);
    }

    public void setEating(boolean eating) {
        this.entityData.set(DATA_EATING, eating);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new DuckFleeFlyGoal(this));
        this.goalSelector.addGoal(2, new DuckWanderGoal(this));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
    }

    @Override
    public boolean isNoGravity() {
        return this.isFlying() || super.isNoGravity();
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean result = super.hurt(source, amount);
        if (result && !this.level().isClientSide) {
            this.recentlyHurt = true;
        }
        return result;
    }

    boolean consumeRecentlyHurt() {
        if (this.recentlyHurt) {
            this.recentlyHurt = false;
            return true;
        }
        return false;
    }

    @Override
    public void aiStep() {
        super.aiStep();

        if (!this.isFlying() && this.isInWater()) {
            this.floatOnWaterSurface();
        }

        if (this.level().isClientSide) return;

        boolean stationary = !this.isFlying() && this.onGround()
                && this.getDeltaMovement().horizontalDistanceSqr() < 0.0025;

        if (!stationary) {
            if (this.isEating()) this.setEating(false);
            this.eatTimer = 0;
            return;
        }

        if (this.eatTimer > 0) {
            this.eatTimer--;
            if (this.eatTimer == 0) this.setEating(false);
        } else if (this.random.nextFloat() < 0.004F) {
            this.setEating(true);
            this.eatTimer = 40 + this.random.nextInt(40);
        }
    }

    // Sits calmly on top of the water instead of the default land-mob behavior
    // of repeatedly hopping/bobbing (FloatGoal) to keep from sinking. Clamped
    // slightly *below* the exact surface so the hitbox reliably still overlaps
    // the fluid (isInWater() reads false if the box floats fully above it),
    // and movement is purely horizontal-target driven (see DuckWanderGoal) so
    // nothing is simultaneously trying to path the duck underwater and fighting
    // this correction.
    private void floatOnWaterSurface() {
        BlockPos pos = this.blockPosition();
        net.minecraft.world.level.material.FluidState fluidState = this.level().getFluidState(pos);
        if (fluidState.isEmpty()) return;

        double surfaceY = pos.getY() + fluidState.getHeight(this.level(), pos) - 0.1;
        double currentY = this.getY();
        if (Math.abs(currentY - surfaceY) > 0.02) {
            this.setPos(this.getX(), Mth.lerp(0.5, currentY, surfaceY), this.getZ());
        }

        Vec3 delta = this.getDeltaMovement();
        if (delta.y < 0) {
            this.setDeltaMovement(delta.x, 0, delta.z);
        }
    }

    public static boolean checkDuckSpawnRules(EntityType<DuckEntity> type, ServerLevelAccessor level,
                                               MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        return level.getFluidState(pos).is(FluidTags.WATER)
                && !level.getBiomeManager().getBiome(pos).is(BiomeTags.IS_OCEAN)
                && level.getRawBrightness(pos, 0) > 8;
    }

    @Override
    public boolean checkSpawnObstruction(LevelReader level) {
        return level.isUnobstructed(this);
    }

    @javax.annotation.Nullable
    @Override
    public net.minecraft.world.entity.AgeableMob getBreedOffspring(net.minecraft.server.level.ServerLevel level,
                                                                    net.minecraft.world.entity.AgeableMob otherParent) {
        return null;
    }

    // --- GeckoLib ---
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "stateController", 0, this::statePredicate));
    }

    private PlayState statePredicate(AnimationState<DuckEntity> state) {
        boolean moving = this.getDeltaMovement().horizontalDistanceSqr() > 0.0025;
        if (this.isFlying()) {
            state.getController().setAnimation(FLY_ANIM);
        } else if (this.isEating()) {
            state.getController().setAnimation(EAT_ANIM);
        } else if (moving) {
            state.getController().setAnimation(this.isInWater() ? SWIM_ANIM : WALK_ANIM);
        } else {
            state.getController().setAnimation(IDLE_ANIM);
        }
        return PlayState.CONTINUE;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // ==========================================================
    // Flees into the air when a player closes in, an arrow lands
    // nearby, or the duck takes damage (covers musket hits, which
    // are hitscan and have no projectile entity to detect).
    // Flight steers away from the threat and avoids flying into blocks.
    // ==========================================================
    private static class DuckFleeFlyGoal extends Goal {
        private final DuckEntity duck;
        private double targetX, targetY, targetZ;
        private int flightTimer = 0;
        private int groundedCooldown = 0;
        private boolean landing = false;
        private int landingTimeout = 0;

        DuckFleeFlyGoal(DuckEntity duck) {
            this.duck = duck;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (duck.isFlying()) return true;
            if (groundedCooldown-- > 0) return false;
            if (duck.consumeRecentlyHurt()) return true;

            Player nearby = duck.level().getNearestPlayer(duck.getX(), duck.getY(), duck.getZ(), THREAT_RADIUS, false);
            if (nearby != null) return true;

            List<AbstractArrow> arrows = duck.level().getEntitiesOfClass(AbstractArrow.class,
                    duck.getBoundingBox().inflate(THREAT_RADIUS));
            return !arrows.isEmpty();
        }

        @Override
        public boolean canContinueToUse() {
            return duck.isFlying();
        }

        @Override
        public void start() {
            duck.setFlying(true);
            duck.setEating(false);
            landing = false;
            pickFleeTarget();
            flightTimer = 100 + duck.random.nextInt(100);
        }

        @Override
        public void tick() {
            if (landing) {
                tickLanding();
                return;
            }

            flightTimer--;
            Vec3 pos = duck.position();
            Vec3 target = new Vec3(targetX, targetY, targetZ);
            Vec3 diff = target.subtract(pos);

            if (diff.lengthSqr() < 1.0) {
                pickFleeTarget();
            } else {
                Vec3 motion = diff.normalize().scale(0.2);
                Vec3 nextPos = pos.add(motion.scale(4.0));

                if (isPathBlocked(pos, nextPos)) {
                    pickFleeTarget();
                    return;
                }

                duck.setDeltaMovement(motion);
                duck.move(MoverType.SELF, duck.getDeltaMovement());
                faceMovementDirection(motion);
            }

            if (flightTimer <= 0) {
                beginLanding();
            }
        }

        @Override
        public void stop() {
            duck.setFlying(false);
            duck.setDeltaMovement(duck.getDeltaMovement().x, 0, duck.getDeltaMovement().z);
            landing = false;
        }

        // Once the threat's outrun, the duck picks a landing spot offset randomly
        // to the side (not just straight down) and glides diagonally down to the
        // ground/water surface there, instead of dropping like a plumb line.
        private void beginLanding() {
            landing = true;
            landingTimeout = 200;

            Vec3 pos = duck.position();
            double angle = duck.random.nextDouble() * Math.PI * 2;
            double dist = 2.0 + duck.random.nextDouble() * 5.0;
            double landX = pos.x + Math.cos(angle) * dist;
            double landZ = pos.z + Math.sin(angle) * dist;

            Vec3 from = new Vec3(landX, pos.y, landZ);
            Vec3 down = new Vec3(landX, duck.level().getMinBuildHeight(), landZ);
            ClipContext clipContext = new ClipContext(from, down, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, duck);
            HitResult hit = duck.level().clip(clipContext);

            Vec3 landSpot = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : down;
            targetX = landSpot.x;
            targetY = landSpot.y + 0.1;
            targetZ = landSpot.z;
        }

        private void tickLanding() {
            if (duck.onGround() || duck.isInWater() || --landingTimeout <= 0) {
                duck.setFlying(false);
                groundedCooldown = 40 + duck.random.nextInt(60);
                return;
            }

            Vec3 pos = duck.position();
            Vec3 target = new Vec3(targetX, targetY, targetZ);
            Vec3 diff = target.subtract(pos);

            if (diff.lengthSqr() < 0.3) {
                duck.setFlying(false);
                groundedCooldown = 40 + duck.random.nextInt(60);
                return;
            }

            Vec3 motion = diff.normalize().scale(0.15);
            Vec3 nextPos = pos.add(motion.scale(4.0));
            if (isPathBlocked(pos, nextPos)) {
                beginLanding(); // pick a fresh spot rather than flying into whatever's in the way
                return;
            }

            duck.setDeltaMovement(motion);
            duck.move(MoverType.SELF, duck.getDeltaMovement());
            faceMovementDirection(motion);
        }

        private void pickFleeTarget() {
            Vec3 pos = duck.position();
            Player threat = duck.level().getNearestPlayer(duck.getX(), duck.getY(), duck.getZ(), THREAT_RADIUS * 2, false);

            Vec3 away;
            if (threat != null) {
                away = pos.subtract(threat.position());
                away = away.horizontalDistanceSqr() < 1.0E-4
                        ? new Vec3(duck.random.nextDouble() - 0.5, 0, duck.random.nextDouble() - 0.5).normalize()
                        : new Vec3(away.x, 0, away.z).normalize();
            } else {
                double angle = duck.random.nextDouble() * Math.PI * 2;
                away = new Vec3(Math.cos(angle), 0, Math.sin(angle));
            }

            double dist = 5.0 + duck.random.nextDouble() * 4.0;
            targetX = pos.x + away.x * dist;
            targetY = pos.y + 2.0 + duck.random.nextDouble() * 2.0;
            targetZ = pos.z + away.z * dist;
        }

        private boolean isPathBlocked(Vec3 from, Vec3 to) {
            ClipContext clipContext = new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, duck);
            HitResult hit = duck.level().clip(clipContext);
            return hit.getType() == HitResult.Type.BLOCK;
        }

        private void faceMovementDirection(Vec3 motion) {
            if (motion.horizontalDistanceSqr() < 1.0E-5) return;
            float targetYaw = (float) (Mth.atan2(-motion.x, motion.z) * (180.0 / Math.PI));
            float newYaw = Mth.rotateIfNecessary(duck.getYRot(), targetYaw, 15.0F);
            duck.setYRot(newYaw);
            duck.yBodyRot = newYaw;
            duck.setYHeadRot(newYaw);
        }
    }

    // ==========================================================
    // Casual wandering on land or in water. Only ever picks a purely
    // horizontal target (never a specific depth/Y) and only sets
    // horizontal velocity intent, leaving vertical placement entirely
    // to floatOnWaterSurface()/gravity — so there's nothing fighting
    // the surface-float correction over what Y the duck should be at.
    // ==========================================================
    private static class DuckWanderGoal extends Goal {
        private final DuckEntity duck;
        private double targetX, targetZ;
        private int wanderTimer = 0;
        private int idleTimer = 0;

        DuckWanderGoal(DuckEntity duck) {
            this.duck = duck;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (idleTimer > 0) {
                idleTimer--;
                return false;
            }
            return duck.random.nextInt(20) == 0;
        }

        @Override
        public boolean canContinueToUse() {
            return wanderTimer > 0;
        }

        @Override
        public void start() {
            pickTarget();
            wanderTimer = 60 + duck.random.nextInt(100);
        }

        @Override
        public void tick() {
            wanderTimer--;
            Vec3 pos = duck.position();
            Vec3 diff = new Vec3(targetX - pos.x, 0, targetZ - pos.z);

            if (diff.lengthSqr() < 0.25) {
                wanderTimer = 0;
                duck.setDeltaMovement(0, duck.getDeltaMovement().y, 0);
                return;
            }

            Vec3 dir = diff.normalize();
            double speed = 0.06;
            duck.setDeltaMovement(dir.x * speed, duck.getDeltaMovement().y, dir.z * speed);

            float targetYaw = (float) (Mth.atan2(-dir.x, dir.z) * (180.0 / Math.PI));
            float newYaw = Mth.rotateIfNecessary(duck.getYRot(), targetYaw, 10.0F);
            duck.setYRot(newYaw);
            duck.yBodyRot = newYaw;
            duck.setYHeadRot(newYaw);
        }

        @Override
        public void stop() {
            duck.setDeltaMovement(0, duck.getDeltaMovement().y, 0);
            idleTimer = 40 + duck.random.nextInt(80);
        }

        private void pickTarget() {
            Vec3 pos = duck.position();
            double angle = duck.random.nextDouble() * Math.PI * 2;
            double dist = 2.0 + duck.random.nextDouble() * 4.0;
            targetX = pos.x + Math.cos(angle) * dist;
            targetZ = pos.z + Math.sin(angle) * dist;
        }
    }
}
