package net.finnigan.tommemod.entity.custom;

import net.finnigan.tommemod.entity.custom.SandSharkHelpers.SandSharkLeapGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.Animation;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Desert ambusher that cruises with its back out of the sand - the "half submerged" look is a render
 * offset (see SandSharkRenderer), not a real hitbox change, so it still fights and collides normally.
 * Fast, steps a full block up so dunes don't stop it, and closes long gaps with a cyclops-style leap.
 */
public class SandSharkEntity extends Monster implements GeoEntity {

    /** How deep into its own block the model sits - half the model's 1.24-block height. */
    public static final float SUBMERGE_DEPTH = 0.6F;

    private static final EntityDataAccessor<Boolean> DATA_LEAPING =
            SynchedEntityData.defineId(SandSharkEntity.class, EntityDataSerializers.BOOLEAN);

    private static final RawAnimation SWIM_ANIM = RawAnimation.begin().thenLoop("swim");
    private static final RawAnimation BITE_ANIM = RawAnimation.begin().then("floor_bite", Animation.LoopType.PLAY_ONCE);
    private static final RawAnimation JUMP_ANIM = RawAnimation.begin().then("jump", Animation.LoopType.PLAY_ONCE);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int leapCooldown;

    public SandSharkEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.setMaxUpStep(1.0F); // "hops up blocks"
        this.xpReward = 10;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.4D) // fast
                .add(Attributes.ATTACK_DAMAGE, 6.0D)
                .add(Attributes.ARMOR, 2.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_LEAPING, false);
    }

    public boolean isLeaping() {
        return this.entityData.get(DATA_LEAPING);
    }

    public void setLeaping(boolean leaping) {
        this.entityData.set(DATA_LEAPING, leaping);
    }

    public boolean isLeapReady() {
        return this.leapCooldown <= 0;
    }

    public void startLeapCooldown() {
        this.leapCooldown = SandSharkLeapGoal.LEAP_COOLDOWN_TICKS;
    }

    // Ticks down regardless of which goal is running, so the leap actually comes back off cooldown.
    @Override
    public void aiStep() {
        if (this.leapCooldown > 0) {
            this.leapCooldown--;
        }
        super.aiStep();
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new SandSharkLeapGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, false));
        this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 12.0F));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /**
     * Daytime desert only, and it has to be sitting in sand for the half-buried look to read. No light
     * check - it is a daylight monster by design, which is also why it never burns.
     */
    public static boolean checkSandSharkSpawnRules(EntityType<SandSharkEntity> type, ServerLevelAccessor level,
                                                   MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        return level.getBlockState(pos.below()).is(BlockTags.SAND)
                && level.getLevel().isDay()
                && level.getDifficulty() != net.minecraft.world.Difficulty.PEACEFUL;
    }

    @Override
    public boolean isSunBurnTick() {
        return false;
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (this.isLeaping()) return false;

        boolean result = super.doHurtTarget(target);
        if (result) {
            this.triggerAnim("actionController", "floor_bite");
        }
        return result;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.SAND_STEP;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.HOGLIN_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.HOGLIN_DEATH;
    }

    // --- GeckoLib ---

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "locomotionController", 3, this::locomotionPredicate));
        controllers.add(new AnimationController<>(this, "actionController", 0, state -> PlayState.STOP)
                .triggerableAnim("floor_bite", BITE_ANIM)
                .triggerableAnim("jump", JUMP_ANIM));
    }

    private PlayState locomotionPredicate(AnimationState<SandSharkEntity> state) {
        if (state.isMoving() || this.isLeaping()) {
            state.getController().setAnimation(SWIM_ANIM);
            return PlayState.CONTINUE;
        }
        // No idle animation for this model - hold the rest pose when it stops.
        return PlayState.STOP;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
