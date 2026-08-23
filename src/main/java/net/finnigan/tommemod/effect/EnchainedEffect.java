package net.finnigan.tommemod.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/** Lanterna's ten-hit buildup. Amplifier 0-8 represents one through nine active stacks. */
public class EnchainedEffect extends MobEffect {
    public static final int STACK_DURATION_TICKS = 80;
    public static final int REQUIRED_STACKS = 10;
    private static final String NEXT_DAMAGE_TAG = "tommemod_enchained_next_damage";

    public EnchainedEffect() {
        super(MobEffectCategory.HARMFUL, 0xD65A00);
    }

    /** Adds and refreshes a stack. Returns true when the tenth stack is reached and consumed. */
    public static boolean addStack(LivingEntity target) {
        MobEffect effect = ModMobEffects.ENCHAINED.get();
        MobEffectInstance current = target.getEffect(effect);
        if (current == null) {
            target.getPersistentData().putLong(NEXT_DAMAGE_TAG, target.level().getGameTime() + 40L);
        }
        int stacks = current == null ? 1 : current.getAmplifier() + 2;
        if (stacks >= REQUIRED_STACKS) {
            target.removeEffect(effect);
            target.getPersistentData().remove(NEXT_DAMAGE_TAG);
            return true;
        }
        target.addEffect(new MobEffectInstance(effect, STACK_DURATION_TICKS, stacks - 1,
                false, true, true));
        return false;
    }

    /** Adds a visible Enchained stack during the triggered chain attack without retriggering it. */
    public static void setTriggeredBonusStacks(LivingEntity target, int stacks, int remainingTicks) {
        MobEffect effect = ModMobEffects.ENCHAINED.get();
        if (!target.hasEffect(effect)) {
            target.getPersistentData().putLong(NEXT_DAMAGE_TAG, target.level().getGameTime() + 40L);
        }
        target.addEffect(new MobEffectInstance(effect, Math.max(1, remainingTicks), Math.max(0, stacks - 1),
                false, true, true));
    }

    public static void clear(LivingEntity target) {
        target.removeEffect(ModMobEffects.ENCHAINED.get());
        target.getPersistentData().remove(NEXT_DAMAGE_TAG);
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        long now = entity.level().getGameTime();
        if (now >= entity.getPersistentData().getLong(NEXT_DAMAGE_TAG)) {
            entity.hurt(entity.damageSources().magic(), 4.0F);
            entity.getPersistentData().putLong(NEXT_DAMAGE_TAG, now + 40L);
        }
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true;
    }
}
