package net.finnigan.tommemod.entity.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
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

import java.util.EnumSet;

/**
 * Deep-cave sprinter. The moment it lays eyes on a player it plants itself, screams for a full second
 * and a quarter, and only then comes at them - so the scream is the tell that something very fast is
 * already on its way.
 */
public class ScreamingEntity extends Monster implements GeoEntity {

    /** Deepest caves only. */
    private static final int MAX_SPAWN_Y = 0;

    private static final EntityDataAccessor<Boolean> DATA_SCREAMING =
            SynchedEntityData.defineId(ScreamingEntity.class, EntityDataSerializers.BOOLEAN);

    private static final RawAnimation RUN_ANIM = RawAnimation.begin().thenLoop("run");
    private static final RawAnimation WALK_ANIM = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation SCREAM_ANIM = RawAnimation.begin().thenPlayAndHold("scream");
    private static final RawAnimation ATTACK_ANIM = RawAnimation.begin().then("attack", Animation.LoopType.PLAY_ONCE);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public ScreamingEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 10;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 12.0D)
                .add(Attributes.ATTACK_DAMAGE, 6.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.60D) // very fast
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_SCREAMING, false);
    }

    public boolean isScreaming() {
        return this.entityData.get(DATA_SCREAMING);
    }

    public void setScreaming(boolean screaming) {
        this.entityData.set(DATA_SCREAMING, screaming);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new ScreamGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, false));
        this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 0.7D));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 12.0F));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    public static boolean checkScreamingSpawnRules(EntityType<ScreamingEntity> type, ServerLevelAccessor level,
                                                   MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        return pos.getY() <= MAX_SPAWN_Y && checkMonsterSpawnRules(type, level, spawnType, pos, random);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (this.isScreaming()) return false;

        boolean result = super.doHurtTarget(target);
        if (result) {
            this.triggerAnim("attackController", "attack");
        }
        return result;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.WARDEN_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WARDEN_DEATH;
    }

    // --- GeckoLib ---

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "locomotionController", 3, this::locomotionPredicate));
        controllers.add(new AnimationController<>(this, "attackController", 0, state -> PlayState.STOP)
                .triggerableAnim("attack", ATTACK_ANIM));
    }

    private PlayState locomotionPredicate(AnimationState<ScreamingEntity> state) {
        if (this.isScreaming()) {
            state.getController().setAnimation(SCREAM_ANIM);
            return PlayState.CONTINUE;
        }
        if (!state.isMoving()) {
            // no idle animation on this model - hold the rest pose
            return PlayState.STOP;
        }
        boolean hunting = this.getTarget() != null && this.getTarget().isAlive();
        state.getController().setAnimation(hunting ? RUN_ANIM : WALK_ANIM);
        return PlayState.CONTINUE;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    // ==========================================================
    // Freezes it in place for one "scream" animation the first time it sights a given target, then
    // hands over to MeleeAttackGoal for the chase.
    // ==========================================================
    private static class ScreamGoal extends Goal {

        /** Length of the "scream" animation (1.25s), in ticks. */
        private static final int SCREAM_TICKS = 25;

        private final ScreamingEntity screaming;
        private LivingEntity screamedAt;
        private int screamTicks;

        ScreamGoal(ScreamingEntity screaming) {
            this.screaming = screaming;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = screaming.getTarget();
            // one scream per target, not one per tick of eye contact
            return target != null && target.isAlive() && target != screamedAt && screaming.hasLineOfSight(target);
        }

        @Override
        public boolean canContinueToUse() {
            return screamTicks > 0;
        }

        @Override
        public void start() {
            screamedAt = screaming.getTarget();
            screamTicks = SCREAM_TICKS;
            screaming.getNavigation().stop();
            screaming.setScreaming(true);
            screaming.level().playSound(null, screaming.blockPosition(), SoundEvents.WARDEN_ROAR,
                    SoundSource.HOSTILE, 10.0F, 3.0F);
        }

        @Override
        public void stop() {
            screaming.setScreaming(false);
            screamTicks = 0;
        }

        @Override
        public void tick() {
            screamTicks--;
            screaming.setDeltaMovement(0.0D, screaming.getDeltaMovement().y, 0.0D);
            if (screamedAt != null) {
                screaming.getLookControl().setLookAt(screamedAt, 30.0F, 30.0F);
            }
        }
    }
}
