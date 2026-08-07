package net.finnigan.tommemod.entity.custom;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;

/**
 * Giant pill bug. Slow and harmless while roaming, but the instant anything hits it, it balls up and
 * sits on the last frame of "curl" taking a tenth of incoming damage. It only unrolls once it has been
 * left alone for a few seconds.
 *
 * Does not spawn naturally: no spawn placement, no biome modifier.
 */
public class RolliePollieEntity extends PathfinderMob implements GeoEntity {

    public static final byte STATE_ROAMING = 0;
    public static final byte STATE_CURLED = 1;
    public static final byte STATE_OPENING = 2;

    /** Damage taken while balled up. */
    private static final float CURLED_DAMAGE_MULTIPLIER = 0.1F;
    /** How long it must go un-hit before it starts to unroll. */
    private static final int UNDISTURBED_TICKS = 100;
    /** Length of the "open" animation (1.625s), in ticks. */
    private static final int OPEN_ANIM_TICKS = 33;

    private static final EntityDataAccessor<Byte> DATA_STATE =
            SynchedEntityData.defineId(RolliePollieEntity.class, EntityDataSerializers.BYTE);

    private static final RawAnimation SCUTTLE_ANIM = RawAnimation.begin().thenLoop("scuttle");
    private static final RawAnimation TWITCH_ANIM = RawAnimation.begin().thenLoop("twitch");
    private static final RawAnimation CURL_ANIM = RawAnimation.begin().thenPlayAndHold("curl");
    private static final RawAnimation OPEN_ANIM = RawAnimation.begin().thenPlayAndHold("open");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int ticksSinceDamaged;
    private int openTicks;

    public RolliePollieEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.xpReward = 5;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 30.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.15D) // deliberately slow
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5D)
                .add(Attributes.FOLLOW_RANGE, 16.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_STATE, STATE_ROAMING);
    }

    public byte getCurlState() {
        return this.entityData.get(DATA_STATE);
    }

    private void setCurlState(byte state) {
        this.entityData.set(DATA_STATE, state);
    }

    public boolean isCurled() {
        return this.getCurlState() == STATE_CURLED;
    }

    /** True whenever it is balled up or mid-unroll - i.e. whenever it must not walk. */
    public boolean isImmobile() {
        return this.getCurlState() != STATE_ROAMING;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new RolliePollieCurlGoal(this));
        this.goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean wasCurled = this.isCurled();
        float dealt = wasCurled ? amount * CURLED_DAMAGE_MULTIPLIER : amount;

        boolean hurt = super.hurt(source, dealt);
        if (hurt && !this.level().isClientSide) {
            this.ticksSinceDamaged = 0;
            if (this.getCurlState() != STATE_CURLED) {
                // curling from either roaming or a half-finished unroll
                this.setCurlState(STATE_CURLED);
                this.openTicks = 0;
                this.getNavigation().stop();
            }
        }
        return hurt;
    }

    @Override
    public void aiStep() {
        if (!this.level().isClientSide) {
            switch (this.getCurlState()) {
                case STATE_CURLED -> {
                    if (++this.ticksSinceDamaged >= UNDISTURBED_TICKS) {
                        this.setCurlState(STATE_OPENING);
                        this.openTicks = OPEN_ANIM_TICKS;
                    }
                }
                case STATE_OPENING -> {
                    if (--this.openTicks <= 0) {
                        this.setCurlState(STATE_ROAMING);
                    }
                }
                default -> { /* roaming - nothing to time */ }
            }
        }
        super.aiStep();
    }

    // --- GeckoLib ---

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "stateController", 3, this::statePredicate));
    }

    private PlayState statePredicate(AnimationState<RolliePollieEntity> state) {
        switch (this.getCurlState()) {
            // "curl" is authored as hold_on_last_frame, so staying on this animation is what keeps it
            // balled up on the final frame for as long as it needs to be.
            case STATE_CURLED -> state.getController().setAnimation(CURL_ANIM);
            case STATE_OPENING -> state.getController().setAnimation(OPEN_ANIM);
            default -> state.getController().setAnimation(state.isMoving() ? SCUTTLE_ANIM : TWITCH_ANIM);
        }
        return PlayState.CONTINUE;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    // ==========================================================
    // Holds the movement flags for as long as it is balled up or unrolling, which is what actually
    // stops the stroll goal from dragging a curled-up bug around.
    // ==========================================================
    private static class RolliePollieCurlGoal extends Goal {

        private final RolliePollieEntity rolliePollie;

        RolliePollieCurlGoal(RolliePollieEntity rolliePollie) {
            this.rolliePollie = rolliePollie;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return rolliePollie.isImmobile();
        }

        @Override
        public boolean canContinueToUse() {
            return rolliePollie.isImmobile();
        }

        @Override
        public void start() {
            rolliePollie.getNavigation().stop();
        }

        @Override
        public void tick() {
            rolliePollie.setDeltaMovement(0.0D, rolliePollie.getDeltaMovement().y, 0.0D);
        }
    }
}
