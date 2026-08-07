package net.finnigan.tommemod.entity.custom;

import net.finnigan.tommemod.entity.custom.FalconHelpers.FalconDiveGoal;
import net.finnigan.tommemod.entity.custom.FalconHelpers.FalconHopGoal;
import net.finnigan.tommemod.entity.custom.FalconHelpers.FalconPatrolGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;

/**
 * Fish-hunting raptor. Spends most of its time patrolling in the air (see FalconPatrolGoal), drops on
 * fish swimming just under the surface (FalconDiveGoal), and once grounded moves exactly like a Birdie
 * - discrete hops that only travel during the short "hop" animation (FalconHopGoal), "idle" otherwise.
 */
public class FalconEntity extends Animal implements GeoEntity {

    /** Length of the "eat" animation (0.4583s), in ticks - how long the eating pose is held after a catch. */
    private static final int EAT_ANIM_TICKS = 10;

    private static final EntityDataAccessor<Boolean> DATA_FLYING =
            SynchedEntityData.defineId(FalconEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_HOPPING =
            SynchedEntityData.defineId(FalconEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_DIVING =
            SynchedEntityData.defineId(FalconEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_EATING =
            SynchedEntityData.defineId(FalconEntity.class, EntityDataSerializers.BOOLEAN);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int eatingTicks;

    public FalconEntity(EntityType<? extends Animal> type, Level level) {
        super(type, level);
        this.xpReward = 3;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 8.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FLYING_SPEED, 0.9D)
                .add(Attributes.FOLLOW_RANGE, 24.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_FLYING, false);
        this.entityData.define(DATA_HOPPING, false);
        this.entityData.define(DATA_DIVING, false);
        this.entityData.define(DATA_EATING, false);
    }

    public boolean isFlying() {
        return this.entityData.get(DATA_FLYING);
    }

    public void setFlying(boolean flying) {
        this.entityData.set(DATA_FLYING, flying);
    }

    public boolean isHopping() {
        return this.entityData.get(DATA_HOPPING);
    }

    public void setHopping(boolean hopping) {
        this.entityData.set(DATA_HOPPING, hopping);
    }

    public boolean isDiving() {
        return this.entityData.get(DATA_DIVING);
    }

    public void setDiving(boolean diving) {
        this.entityData.set(DATA_DIVING, diving);
    }

    public boolean isEating() {
        return this.entityData.get(DATA_EATING);
    }

    /** Called by FalconDiveGoal the moment a fish is killed; clears itself once the animation is over. */
    public void startEating() {
        this.entityData.set(DATA_EATING, true);
        this.eatingTicks = EAT_ANIM_TICKS;
    }

    @Override
    public void aiStep() {
        if (this.eatingTicks > 0 && --this.eatingTicks == 0) {
            this.entityData.set(DATA_EATING, false);
        }
        super.aiStep();
    }

    @Override
    public boolean isNoGravity() {
        // The patrol and dive goals drive position directly, so gravity has to stay off while airborne.
        return this.isFlying() || this.isDiving() || super.isNoGravity();
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
        // a diving bird lands on purpose
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new FalconDiveGoal(this));
        this.goalSelector.addGoal(2, new FalconPatrolGoal(this));
        this.goalSelector.addGoal(3, new FalconHopGoal(this, 1.0D));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
    }

    /**
     * Lakeshores and forests, explicitly never oceans. The biome modifier supplies the forest/river/
     * plains/swamp candidates; this rejects anything that is actually ocean.
     */
    public static boolean checkFalconSpawnRules(EntityType<FalconEntity> type, ServerLevelAccessor level,
                                                MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        return level.getBlockState(pos.below()).is(BlockTags.ANIMALS_SPAWNABLE_ON)
                && level.getRawBrightness(pos, 0) > 8
                && !level.getBiome(pos).is(BiomeTags.IS_OCEAN)
                && level.getBiome(pos).is(BiomeTags.IS_OVERWORLD);
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
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Flying", this.isFlying());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.setFlying(tag.getBoolean("Flying"));
    }

    // --- GeckoLib ---

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "stateController", 0, this::statePredicate));
    }

    private PlayState statePredicate(AnimationState<FalconEntity> state) {
        if (this.isDiving()) {
            state.getController().setAnimation(RawAnimation.begin().thenPlayAndHold("dive"));
        } else if (this.isEating()) {
            state.getController().setAnimation(RawAnimation.begin().thenPlay("eat"));
        } else if (this.isFlying()) {
            state.getController().setAnimation(RawAnimation.begin().thenLoop("fly"));
        } else if (this.isHopping()) {
            state.getController().setAnimation(RawAnimation.begin().thenLoop("hop"));
        } else {
            state.getController().setAnimation(RawAnimation.begin().thenLoop("idle"));
        }
        return PlayState.CONTINUE;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
