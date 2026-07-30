package net.finnigan.tommemod.entity.custom;

import net.finnigan.tommemod.entity.custom.Bosses.BossCrab.CrabHitboxUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MobType;
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
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
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
 * Undead field guardian: attacks like a normal mob (vanilla MeleeAttackGoal + doHurtTarget), randomly
 * playing swing_left or swing_right. Around 5% of the time it instead winds up ("spin_prep") into a
 * spin: 5 damage pulses to everything nearby, one every half second. Burns in daylight like other
 * undead. Never spawns naturally except at night (see ModEvents/biome_modifier - same weight as
 * Living Armor).
 */
public class ScarecrowEntity extends Monster implements GeoEntity {

    private static final double SPIN_CHANCE = 0.25;
    private static final int SPIN_PREP_DURATION_TICKS = 5;   // matches spin_prep (0.25s)
    private static final int SPIN_PULSE_INTERVAL_TICKS = 10; // matches spin (0.5s)
    private static final int SPIN_TOTAL_PULSES = 5;
    private static final double SPIN_RADIUS = 3.0;
    private static final double SPIN_VERTICAL_REACH = 2.0;

    private static final EntityDataAccessor<Boolean> DATA_SPINNING =
            SynchedEntityData.defineId(ScarecrowEntity.class, EntityDataSerializers.BOOLEAN);

    private enum SpinPhase { NONE, WINDUP, PULSING }

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private SpinPhase spinPhase = SpinPhase.NONE;
    private int spinPhaseTicksRemaining;
    private int spinPulsesRemaining;

    public ScarecrowEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 60.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.16D)
                .add(Attributes.ATTACK_DAMAGE, 10.0D)
                .add(Attributes.ARMOR, 2.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6)
                .add(Attributes.FOLLOW_RANGE, 24.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_SPINNING, false);
    }

    public boolean isSpinning() {
        return this.entityData.get(DATA_SPINNING);
    }

    @Override
    public MobType getMobType() {
        return MobType.UNDEAD;
    }

    // Undead: catches fire in direct sunlight like zombies/skeletons. Also holds still while spinning
    // so the goal selector's movement can't fight the spin sequence mid-attack.
    @Override
    public void aiStep() {
        if (isSpinning()) {
            this.getNavigation().stop();
        }
        if (this.isAlive() && this.isSunBurnTick()) {
            this.setSecondsOnFire(8);
        }
        super.aiStep();
        if (isSpinning()) {
            this.setDeltaMovement(0, this.getDeltaMovement().y, 0);
        }
    }

    // Drives the spin's windup -> 5 damage pulses sequence directly (not a Goal), so it can't get
    // interrupted or stalled by the goal selector re-evaluating priorities mid-spin.
    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide || spinPhase == SpinPhase.NONE) return;

        if (--spinPhaseTicksRemaining > 0) return;

        if (spinPhase == SpinPhase.WINDUP) {
            spinPhase = SpinPhase.PULSING;
            spinPulsesRemaining = SPIN_TOTAL_PULSES;
            firePulse();
        } else if (spinPulsesRemaining > 0) {
            firePulse();
        } else {
            spinPhase = SpinPhase.NONE;
            this.entityData.set(DATA_SPINNING, false);
        }
    }

    private void firePulse() {
        this.triggerAnim("attackController", "spin");
        double damage = this.getAttributeValue(Attributes.ATTACK_DAMAGE);
        for (LivingEntity nearby : CrabHitboxUtil.getNearbyLivingExcludingSelf(this, SPIN_RADIUS)) {
            if (CrabHitboxUtil.isInCylinder(this, nearby, SPIN_RADIUS, SPIN_VERTICAL_REACH)) {
                nearby.hurt(this.damageSources().mobAttack(this), (float) damage);
                Vec3 knockback = nearby.position().subtract(this.position()).normalize().scale(0.5).add(0, 0.2, 0);
                nearby.push(knockback.x, knockback.y, knockback.z);
            }
        }
        spinPulsesRemaining--;
        spinPhaseTicksRemaining = SPIN_PULSE_INTERVAL_TICKS;
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (isSpinning()) return false;

        if (this.random.nextDouble() < SPIN_CHANCE) {
            this.entityData.set(DATA_SPINNING, true);
            spinPhase = SpinPhase.WINDUP;
            spinPhaseTicksRemaining = SPIN_PREP_DURATION_TICKS;
            this.triggerAnim("attackController", "spin_prep");
            return false;
        }

        boolean result = super.doHurtTarget(target);
        if (result) {
            this.triggerAnim("attackController", this.random.nextBoolean() ? "swing_left" : "swing_right");
        }
        return result;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ZOMBIE_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.ZOMBIE_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ZOMBIE_DEATH;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0D, false));
        this.goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // Same spawn rule zombies/Living Armor use: darkness / valid monster spawn light level.
    public static boolean checkScarecrowSpawnRules(EntityType<ScarecrowEntity> type, ServerLevelAccessor level,
                                                    MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        return checkMonsterSpawnRules(type, level, spawnType, pos, random);
    }

    // --- GeckoLib ---

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "walkController", 5, this::walkPredicate));
        controllers.add(new AnimationController<>(this, "attackController", 0, this::attackPredicate)
                .triggerableAnim("swing_left", RawAnimation.begin().then("swing_left", Animation.LoopType.PLAY_ONCE))
                .triggerableAnim("swing_right", RawAnimation.begin().then("swing right", Animation.LoopType.PLAY_ONCE))
                .triggerableAnim("spin_prep", RawAnimation.begin().then("spin_prep", Animation.LoopType.HOLD_ON_LAST_FRAME))
                .triggerableAnim("spin", RawAnimation.begin().then("spin", Animation.LoopType.PLAY_ONCE)));
    }

    private PlayState walkPredicate(AnimationState<ScarecrowEntity> state) {
        double dx = this.getX() - this.xo;
        double dz = this.getZ() - this.zo;
        boolean moving = (dx * dx + dz * dz) > 1.0E-5;

        if (moving) {
            state.getController().setAnimation(RawAnimation.begin().thenLoop("walk"));
            return PlayState.CONTINUE;
        }
        return PlayState.STOP;
    }

    private PlayState attackPredicate(AnimationState<ScarecrowEntity> state) {
        return PlayState.STOP;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
