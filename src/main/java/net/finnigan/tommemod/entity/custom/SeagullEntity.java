package net.finnigan.tommemod.entity.custom;

import net.finnigan.tommemod.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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

import javax.annotation.Nullable;
import java.util.EnumSet;

public class SeagullEntity extends Animal implements GeoEntity {

    private static final EntityDataAccessor<Boolean> DATA_FLYING =
            SynchedEntityData.defineId(SeagullEntity.class, EntityDataSerializers.BOOLEAN);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public SeagullEntity(EntityType<? extends Animal> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 6.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FLYING_SPEED, 0.6);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_FLYING, false);
    }

    public boolean isFlying() {
        return this.entityData.get(DATA_FLYING);
    }

    public void setFlying(boolean flying) {
        this.entityData.set(DATA_FLYING, flying);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new SeagullFlyGoal(this));
        this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(3, new RandomLookAroundGoal(this));
        // Deliberately no wander/strolling goal — grounded seagull stays put until it flies again.
    }

    @Override
    public boolean isNoGravity() {
        return this.isFlying() || super.isNoGravity();
    }

    public static boolean checkSeagullSpawnRules(EntityType<SeagullEntity> type, ServerLevelAccessor level,
                                                 MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        return level.getBlockState(pos.below()).is(net.minecraft.tags.BlockTags.ANIMALS_SPAWNABLE_ON)
                && level.getRawBrightness(pos, 0) > 8;
    }

    @Override
    public boolean checkSpawnObstruction(LevelReader level) {
        return level.isUnobstructed(this);
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
        return null;
    }

    @Override
    protected void dropCustomDeathLoot(net.minecraft.world.damagesource.DamageSource source, int looting, boolean recentlyHit) {
        super.dropCustomDeathLoot(source, looting, recentlyHit);
        if (recentlyHit) {
            this.spawnAtLocation(new ItemStack(net.minecraft.world.item.Items.FEATHER, 1 + this.random.nextInt(2)));
        }
    }

    // --- GeckoLib ---
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "stateController", 0, this::statePredicate));
    }

    private PlayState statePredicate(AnimationState<SeagullEntity> state) {
        if (this.isFlying()) {
            state.getController().setAnimation(
                    RawAnimation.begin().thenPlay("open_wings").thenLoop("fly"));
            return PlayState.CONTINUE;
        }
        return PlayState.STOP;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // ==========================================================
    // Flight goal — mirrors BirdieFlyGoal: obstacle avoidance,
    // faces direction of travel, mostly airborne, no hopping.
    // ==========================================================
    private static class SeagullFlyGoal extends Goal {
        private final SeagullEntity seagull;
        private double targetX, targetY, targetZ;
        private int flightTimer = 0;
        private int groundedCooldown = 20;

        SeagullFlyGoal(SeagullEntity seagull) {
            this.seagull = seagull;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (seagull.isFlying()) return true;
            if (groundedCooldown-- > 0) return false;
            return seagull.random.nextInt(15) == 0;
        }

        @Override
        public boolean canContinueToUse() {
            return seagull.isFlying() && flightTimer > 0;
        }

        @Override
        public void start() {
            seagull.setFlying(true);
            pickNewTarget();
            flightTimer = 400 + seagull.random.nextInt(400);
        }

        @Override
        public void tick() {
            flightTimer--;
            Vec3 pos = seagull.position();
            Vec3 target = new Vec3(targetX, targetY, targetZ);
            Vec3 diff = target.subtract(pos);

            if (diff.lengthSqr() < 1.0) {
                pickNewTarget();
            } else {
                Vec3 motion = diff.normalize().scale(0.18);
                Vec3 nextPos = pos.add(motion.scale(4.0));

                if (isPathBlocked(pos, nextPos)) {
                    pickNewTarget();
                    return;
                }

                seagull.setDeltaMovement(motion);
                seagull.move(MoverType.SELF, seagull.getDeltaMovement());
                seagull.getLookControl().setLookAt(targetX, targetY, targetZ);
                faceMovementDirection(motion);
            }

            if (flightTimer <= 0) {
                seagull.setFlying(false);
                groundedCooldown = 20 + seagull.random.nextInt(40);
            }
        }

        @Override
        public void stop() {
            seagull.setFlying(false);
            seagull.setDeltaMovement(seagull.getDeltaMovement().x, 0, seagull.getDeltaMovement().z);
        }

        private void pickNewTarget() {
            Vec3 pos = seagull.position();
            double angle = seagull.random.nextDouble() * Math.PI * 2;
            double dist = 4.0 + seagull.random.nextDouble() * 6.0;
            targetX = pos.x + Math.cos(angle) * dist;
            targetY = pos.y + 1.0 + seagull.random.nextDouble() * 3.0;
            targetZ = pos.z + Math.sin(angle) * dist;
        }

        private boolean isPathBlocked(Vec3 from, Vec3 to) {
            ClipContext clipContext = new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, seagull);
            HitResult hit = seagull.level().clip(clipContext);
            return hit.getType() == HitResult.Type.BLOCK;
        }

        private void faceMovementDirection(Vec3 motion) {
            if (motion.horizontalDistanceSqr() < 1.0E-5) return;
            float targetYaw = (float) (Mth.atan2(-motion.x, motion.z) * (180.0 / Math.PI));
            float currentYaw = seagull.getYRot();
            float newYaw = Mth.rotateIfNecessary(currentYaw, targetYaw, 15.0F);
            seagull.setYRot(newYaw);
            seagull.yBodyRot = newYaw;
            seagull.setYHeadRot(newYaw);
        }
    }
}