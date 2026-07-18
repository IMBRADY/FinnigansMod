package net.finnigan.tommemod.entity.custom;

import net.finnigan.tommemod.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.ResetUniversalAngerTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.UUID;

public class TigerEntity extends Animal implements NeutralMob, GeoEntity {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private static final UUID PROVOKED_SPEED_MODIFIER_ID = UUID.fromString("8f6c1a2e-4b3d-4f8e-9c1a-2b3c4d5e6f7a");
    private static final double PROVOKED_SPEED_BONUS = 0.15;

    private static final EntityDataAccessor<Boolean> DATA_PROVOKED =
            SynchedEntityData.defineId(TigerEntity.class, EntityDataSerializers.BOOLEAN);

    // --- NeutralMob anger bookkeeping (same fields vanilla Wolf/PolarBear use) ---
    private static final UniformInt PERSISTENT_ANGER_TIME = TimeUtil.rangeOfSeconds(20, 39);
    private int remainingPersistentAngerTime;
    @Nullable
    private UUID persistentAngerTarget;

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_PROVOKED, false);
    }

    public TigerEntity(EntityType<? extends Animal> type, Level level) {
        super(type, level);
        this.moveControl = new MoveControl(this);
    }

    private void updateProvokedSpeedModifier(boolean provoked) {
        AttributeInstance speedAttr = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr == null) return;

        speedAttr.removeModifier(PROVOKED_SPEED_MODIFIER_ID);
        if (provoked) {
            speedAttr.addTransientModifier(new AttributeModifier(
                    PROVOKED_SPEED_MODIFIER_ID,
                    "Provoked speed boost",
                    PROVOKED_SPEED_BONUS,
                    AttributeModifier.Operation.ADDITION));
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 30.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.3, true));
        this.goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 1.0));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
                this::isAngryAt));
        this.targetSelector.addGoal(3, new ResetUniversalAngerTargetGoal<>(this, true));
    }

    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        boolean result = super.doHurtTarget(target);
        if (!this.level().isClientSide) {
            this.triggerAnim("actionController", "attack");
        }
        return result;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean wasAngryBefore = this.getTarget() != null;
        boolean result = super.hurt(source, amount);
        if (result && !this.level().isClientSide && source.getEntity() instanceof net.minecraft.world.entity.LivingEntity attacker) {
            if (!wasAngryBefore) {
                this.triggerAnim("actionController", "roar");
            }
            this.setTarget(attacker);
            this.setPersistentAngerTarget(attacker.getUUID());
            this.startPersistentAngerTimer();
        }
        return result;
    }

    @Override
    public void setTarget(@Nullable net.minecraft.world.entity.LivingEntity target) {
        super.setTarget(target);
        if (!this.level().isClientSide) {
            this.setProvoked(target != null);
            this.updateProvokedSpeedModifier(target != null);
        }
    }

    public boolean isProvoked() {
        return this.entityData.get(DATA_PROVOKED);
    }

    public void setProvoked(boolean provoked) {
        this.entityData.set(DATA_PROVOKED, provoked);
    }

    // --- Jungle-only spawn rule ---
    public static boolean checkTigerSpawnRules(EntityType<TigerEntity> type, ServerLevelAccessor level,
                                               MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        return level.getBlockState(pos.below()).is(net.minecraft.tags.BlockTags.ANIMALS_SPAWNABLE_ON)
                && level.getBiome(pos).is(BiomeTags.IS_JUNGLE);
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

    // --- NeutralMob interface ---
    @Override
    public int getRemainingPersistentAngerTime() {
        return this.remainingPersistentAngerTime;
    }

    @Override
    public void setRemainingPersistentAngerTime(int time) {
        this.remainingPersistentAngerTime = time;
    }

    @Nullable
    @Override
    public UUID getPersistentAngerTarget() {
        return this.persistentAngerTarget;
    }

    @Override
    public void setPersistentAngerTarget(@Nullable UUID target) {
        this.persistentAngerTarget = target;
    }

    @Override
    public void startPersistentAngerTimer() {
        this.setRemainingPersistentAngerTime(PERSISTENT_ANGER_TIME.sample(this.random));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        this.addPersistentAngerSaveData(tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.readPersistentAngerSaveData(this.level(), tag);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide) {
            this.updatePersistentAnger((ServerLevel) this.level(), true);
        }
    }
    /*
    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
        super.dropCustomDeathLoot(source, looting, recentlyHit);
        if (recentlyHit) {
            this.spawnAtLocation(new ItemStack(Items.LEATHER));
        }
    }
    */

    // --- GeckoLib ---
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movementController", 0, this::movementPredicate));
        controllers.add(new AnimationController<>(this, "actionController", 0, this::actionPredicate)
                .triggerableAnim("roar", RawAnimation.begin().thenPlay("roar"))
                .triggerableAnim("attack", RawAnimation.begin().thenPlay("attack")));
    }

    private PlayState movementPredicate(AnimationState<TigerEntity> state) {
        if (state.isMoving()) {
            if (this.isProvoked()) {
                state.getController().setAnimation(RawAnimation.begin().thenLoop("run"));
            } else {
                state.getController().setAnimation(RawAnimation.begin().thenLoop("walk"));
            }
            return PlayState.CONTINUE;
        }
        return PlayState.STOP;
    }

    private PlayState actionPredicate(AnimationState<TigerEntity> state) {
        return PlayState.STOP; // idle state; animation only plays when triggered externally
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}