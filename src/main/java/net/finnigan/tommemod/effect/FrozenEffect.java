package net.finnigan.tommemod.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/** Ixe's "trapped in ixe_box" freeze debuff. Tick-damage and visual wired up in Phase 3a (Ixe). */
public class FrozenEffect extends MobEffect {
    public FrozenEffect() {
        super(MobEffectCategory.HARMFUL, 0x66CCFF);
    }

    private static final float DAMAGE_PER_SECOND = 4.0F;

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return duration % 20 == 0;
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        entity.hurt(entity.damageSources().freeze(), DAMAGE_PER_SECOND);
    }
}
