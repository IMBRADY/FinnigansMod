package net.finnigan.tommemod.entity.custom;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.level.Level;
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
 * An iron golem forged out of an ancient armour fragment (see AncientArmorFragmentEvents). Extends
 * IronGolem outright so it inherits the whole golem package - village defence, target selection,
 * knockback resistance, iron-ingot repair - and only changes what the brief asks for: double health,
 * a permanent slow self-heal, and GeckoLib animations in place of the vanilla model.
 *
 * Does not spawn naturally: it has no spawn placement and no biome modifier.
 */
public class LivingGuardEntity extends IronGolem implements GeoEntity {

    /** Regeneration I heals 1 HP every 50 ticks - matched here directly so no effect (and no particles) is applied. */
    private static final int REGEN_INTERVAL_TICKS = 50;

    // "at ease" is a 0.5s transition that ends on the resting pose, not a static pose, so anything that
    // makes the controller re-enter it replays that whole settle every time. GeckoLib's own
    // AnimationState#isMoving is a limb-swing threshold that flickers false for a tick or two whenever
    // the golem's pathing hitches, which restarts the settle over and over and reads as the guard
    // wandering around mid-animation. Deciding it server-side off actual travelled distance, with a
    // grace period, gives the animation a stable input.
    private static final EntityDataAccessor<Boolean> DATA_WALKING =
            SynchedEntityData.defineId(LivingGuardEntity.class, EntityDataSerializers.BOOLEAN);

    /** Squared blocks-per-tick below which the golem counts as standing still. */
    private static final double WALKING_THRESHOLD_SQR = 1.0E-4D;
    /** Ticks of stillness required before it settles, so a momentary pathing hitch doesn't restart "at ease". */
    private static final int SETTLE_DELAY_TICKS = 6;

    private static final RawAnimation WALK_ANIM = RawAnimation.begin().thenLoop("walk");
    /** Plays the settle-down once, then freezes on its final pose - it should stand still, not fidget on a loop. */
    private static final RawAnimation AT_EASE_ANIM = RawAnimation.begin().thenPlayAndHold("at ease");
    private static final RawAnimation STRIKE_ANIM = RawAnimation.begin().then("strike", Animation.LoopType.PLAY_ONCE);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int regenTicks;
    private int stillTicks;
    private Vec3 lastPosition = Vec3.ZERO;

    public LivingGuardEntity(EntityType<? extends IronGolem> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return IronGolem.createAttributes()
                .add(Attributes.MAX_HEALTH, 200.0D); // 2x the vanilla golem's 100
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_WALKING, false);
    }

    public boolean isWalking() {
        return this.entityData.get(DATA_WALKING);
    }

    @Override
    public void aiStep() {
        if (!this.level().isClientSide && this.isAlive() && this.getHealth() < this.getMaxHealth()) {
            if (++this.regenTicks >= REGEN_INTERVAL_TICKS) {
                this.regenTicks = 0;
                this.heal(1.0F);
            }
        }
        if (!this.level().isClientSide) {
            updateWalkingFlag();
        }
        super.aiStep();
    }

    /**
     * Measured from where it actually ended up last tick rather than from getDeltaMovement, which a
     * pathing mob rewrites every tick and which reads as zero on the frame a path segment ends.
     */
    private void updateWalkingFlag() {
        Vec3 position = this.position();
        boolean movedThisTick = position.distanceToSqr(this.lastPosition) > WALKING_THRESHOLD_SQR;
        this.lastPosition = position;

        this.stillTicks = movedThisTick ? 0 : this.stillTicks + 1;
        this.entityData.set(DATA_WALKING, this.stillTicks < SETTLE_DELAY_TICKS);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean result = super.doHurtTarget(target);
        if (result) {
            this.triggerAnim("attackController", "strike");
        }
        return result;
    }

    // --- GeckoLib ---

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "locomotionController", 5, this::locomotionPredicate));
        controllers.add(new AnimationController<>(this, "attackController", 0, state -> PlayState.STOP)
                .triggerableAnim("strike", STRIKE_ANIM));
    }

    private PlayState locomotionPredicate(AnimationState<LivingGuardEntity> state) {
        state.getController().setAnimation(this.isWalking() ? WALK_ANIM : AT_EASE_ANIM);
        return PlayState.CONTINUE;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
