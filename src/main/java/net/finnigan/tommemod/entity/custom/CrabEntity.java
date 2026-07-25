package net.finnigan.tommemod.entity.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;

public class CrabEntity extends Animal implements GeoEntity {

    private static final EntityDataAccessor<Integer> DATA_VARIANT =
            SynchedEntityData.defineId(CrabEntity.class, EntityDataSerializers.INT);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public CrabEntity(EntityType<? extends Animal> type, Level level) {
        super(type, level);
        this.setMaxUpStep(1.0F); // hops up 1-block ledges without needing to jump
    }

    public enum Variant {
        BLUE, RED;

        public static Variant byId(int id) {
            Variant[] values = values();
            return values[id % values.length];
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 6.0)
                .add(Attributes.MOVEMENT_SPEED, 0.18)
                .add(Attributes.ATTACK_DAMAGE, 2.0)
                .add(Attributes.FOLLOW_RANGE, 12.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_VARIANT, 0);
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType spawnType, @Nullable SpawnGroupData spawnGroupData, @Nullable CompoundTag tag) {
        this.setVariant(Variant.byId(this.random.nextInt(2)));
        return super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData, tag);
    }

    public Variant getVariant() {
        return Variant.byId(this.entityData.get(DATA_VARIANT));
    }

    public void setVariant(Variant variant) {
        this.entityData.set(DATA_VARIANT, variant.ordinal());
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Variant", this.getVariant().ordinal());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.setVariant(Variant.byId(tag.getInt("Variant")));
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0, true));
        this.goalSelector.addGoal(2, new CrabWanderGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean result = super.doHurtTarget(target);
        if (result) {
            this.triggerAnim("stateController", "snap");
        }
        return result;
    }

    public static boolean checkCrabSpawnRules(EntityType<CrabEntity> type, ServerLevelAccessor level,
                                               MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        return level.getBlockState(pos.below()).is(BlockTags.ANIMALS_SPAWNABLE_ON)
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

    // --- GeckoLib ---
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "stateController", 0, this::statePredicate)
                .triggerableAnim("snap", RawAnimation.begin().thenPlay("snap")));
    }

    private PlayState statePredicate(AnimationState<CrabEntity> state) {
        // No idle/"sit" clip exists in crab.animation.json yet, only "crawl" and "snap" —
        // leave the current animation (bind pose, or an in-progress triggered snap) alone
        // when stationary rather than stomping over it.
        if (state.isMoving()) {
            state.getController().setAnimation(RawAnimation.begin().thenLoop("crawl"));
        }
        return PlayState.CONTINUE;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // ==========================================================
    // Crabs walk sideways: body facing is locked perpendicular to
    // the direction of travel instead of turning to face it. Only
    // sets velocity intent (no direct move() call) so vanilla's own
    // travel()/gravity/friction/step-up pipeline does the actual move.
    // ==========================================================
    private static class CrabWanderGoal extends Goal {
        private final CrabEntity crab;
        private double targetX, targetZ;
        private int wanderTimer = 0;
        private int restTimer = 0;
        private boolean facingOffsetPositive;

        CrabWanderGoal(CrabEntity crab) {
            this.crab = crab;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (crab.getTarget() != null) return false;
            if (restTimer > 0) {
                restTimer--;
                return false;
            }
            return crab.random.nextInt(20) == 0;
        }

        @Override
        public boolean canContinueToUse() {
            return crab.getTarget() == null && wanderTimer > 0;
        }

        @Override
        public void start() {
            pickTarget();
            facingOffsetPositive = crab.random.nextBoolean();
            wanderTimer = 60 + crab.random.nextInt(80);
        }

        @Override
        public void tick() {
            wanderTimer--;
            Vec3 pos = crab.position();
            Vec3 diff = new Vec3(targetX - pos.x, 0, targetZ - pos.z);

            if (diff.lengthSqr() < 0.25) {
                wanderTimer = 0;
                crab.setDeltaMovement(0, crab.getDeltaMovement().y, 0);
                return;
            }

            Vec3 dir = diff.normalize();
            double speed = 0.05;
            crab.setDeltaMovement(dir.x * speed, crab.getDeltaMovement().y, dir.z * speed);

            float travelYaw = (float) (Mth.atan2(-dir.x, dir.z) * (180.0 / Math.PI));
            float facingYaw = travelYaw + (facingOffsetPositive ? 90.0F : -90.0F);
            crab.setYRot(facingYaw);
            crab.yBodyRot = facingYaw;
            crab.setYHeadRot(facingYaw);
        }

        @Override
        public void stop() {
            crab.setDeltaMovement(0, crab.getDeltaMovement().y, 0);
            restTimer = 40 + crab.random.nextInt(100);
        }

        private void pickTarget() {
            Vec3 pos = crab.position();
            double angle = crab.random.nextDouble() * Math.PI * 2;
            double dist = 2.0 + crab.random.nextDouble() * 3.0;
            targetX = pos.x + Math.cos(angle) * dist;
            targetZ = pos.z + Math.sin(angle) * dist;
        }
    }
}
