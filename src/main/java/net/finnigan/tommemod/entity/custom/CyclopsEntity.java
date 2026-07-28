package net.finnigan.tommemod.entity.custom;

import net.finnigan.tommemod.entity.custom.CyclopsHelpers.CyclopsLungeGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
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
 * Aggressive giant: walks while wandering, runs once it has a target, attacks like a normal mob
 * (vanilla MeleeAttackGoal + doHurtTarget) at melee range, and lunges (see CyclopsLungeGoal) from
 * medium range - repeatably, on its own cooldown (tracked here on the entity so it keeps counting
 * down between lunges instead of only while the lunge goal itself is active). Never spawns naturally
 * except at night (see ModEvents/biome_modifier - same weight as Living Armor).
 */
public class CyclopsEntity extends Monster implements GeoEntity {

    private static final int LUNGE_COOLDOWN_TICKS = 120;

    private static final EntityDataAccessor<Boolean> DATA_LUNGING =
            SynchedEntityData.defineId(CyclopsEntity.class, EntityDataSerializers.BOOLEAN);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int lungeCooldown;

    public CyclopsEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 40.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.32D)
                .add(Attributes.ATTACK_DAMAGE, 8.0D)
                .add(Attributes.ARMOR, 3.0D)
                .add(Attributes.FOLLOW_RANGE, 28.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_LUNGING, false);
    }

    public boolean isLunging() {
        return this.entityData.get(DATA_LUNGING);
    }

    public void setLunging(boolean lunging) {
        this.entityData.set(DATA_LUNGING, lunging);
    }

    public boolean isLungeReady() {
        return lungeCooldown <= 0;
    }

    public void startLungeCooldown() {
        lungeCooldown = LUNGE_COOLDOWN_TICKS;
    }

    // Always ticks down, regardless of which goal is currently active - this is what lets the lunge
    // actually come off cooldown and fire again, rather than only once ever.
    @Override
    public void aiStep() {
        if (lungeCooldown > 0) {
            lungeCooldown--;
        }
        super.aiStep();
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (isLunging()) return false;

        boolean result = super.doHurtTarget(target);
        if (result) {
            this.triggerAnim("attackController", this.random.nextBoolean() ? "swing_left" : "swing_right");
        }
        return result;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.RAVAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.RAVAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.RAVAGER_DEATH;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new CyclopsLungeGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, false));
        this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // Same spawn rule zombies/Living Armor use: darkness / valid monster spawn light level.
    public static boolean checkCyclopsSpawnRules(EntityType<CyclopsEntity> type, ServerLevelAccessor level,
                                                 MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        return checkMonsterSpawnRules(type, level, spawnType, pos, random);
    }

    // --- GeckoLib ---

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "locomotionController", 5, this::locomotionPredicate));
        controllers.add(new AnimationController<>(this, "attackController", 0, this::attackPredicate)
                .triggerableAnim("swing_left", RawAnimation.begin().then("swing_left", Animation.LoopType.PLAY_ONCE))
                .triggerableAnim("swing_right", RawAnimation.begin().then("swing_right", Animation.LoopType.PLAY_ONCE))
                .triggerableAnim("lunge", RawAnimation.begin().then("lunge", Animation.LoopType.PLAY_ONCE)));
    }

    private PlayState locomotionPredicate(AnimationState<CyclopsEntity> state) {
        double dx = this.getX() - this.xo;
        double dz = this.getZ() - this.zo;
        boolean moving = (dx * dx + dz * dz) > 1.0E-5;
        boolean hasTarget = this.getTarget() != null && this.getTarget().isAlive();

        if (hasTarget) {
            state.getController().setAnimation(moving
                    ? RawAnimation.begin().thenLoop("run")
                    : RawAnimation.begin().thenLoop("idle"));
        } else if (moving) {
            state.getController().setAnimation(RawAnimation.begin().thenLoop("walk"));
        } else {
            state.getController().setAnimation(RawAnimation.begin().thenLoop("idle"));
        }
        return PlayState.CONTINUE;
    }

    private PlayState attackPredicate(AnimationState<CyclopsEntity> state) {
        return PlayState.STOP;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
