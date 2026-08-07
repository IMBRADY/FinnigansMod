package net.finnigan.tommemod.entity.custom;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.level.Level;
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

    private static final RawAnimation WALK_ANIM = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation GUARD_ANIM = RawAnimation.begin().thenLoop("guard");
    private static final RawAnimation STRIKE_ANIM = RawAnimation.begin().then("strike", Animation.LoopType.PLAY_ONCE);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int regenTicks;

    public LivingGuardEntity(EntityType<? extends IronGolem> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return IronGolem.createAttributes()
                .add(Attributes.MAX_HEALTH, 200.0D); // 2x the vanilla golem's 100
    }

    @Override
    public void aiStep() {
        if (!this.level().isClientSide && this.isAlive() && this.getHealth() < this.getMaxHealth()) {
            if (++this.regenTicks >= REGEN_INTERVAL_TICKS) {
                this.regenTicks = 0;
                this.heal(1.0F);
            }
        }
        super.aiStep();
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
        state.getController().setAnimation(state.isMoving() ? WALK_ANIM : GUARD_ANIM);
        return PlayState.CONTINUE;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
