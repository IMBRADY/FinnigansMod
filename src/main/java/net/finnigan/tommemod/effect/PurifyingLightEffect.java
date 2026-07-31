package net.finnigan.tommemod.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Lumapier's passive: poison/wither/nausea immunity + torch-level light while active.
 * The immunity logic itself lives in event.LumapierEventHelpers.PurifyingLightImmunityHandler, which
 * intercepts net.minecraftforge.event.entity.living.MobEffectEvent.Applicable (confirmed present in
 * this Forge version) and denies Poison/Wither/Confusion(nausea) while this effect is active - Forge
 * events can only be subscribed to from an @Mod.EventBusSubscriber class, not from a MobEffect
 * subclass, hence the split. Light emission (torch parity) is handled separately by
 * event.LumapierEventHelpers.LumapierLightHandler via the invisible-light-block technique.
 */
public class PurifyingLightEffect extends MobEffect {
    public PurifyingLightEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xFFF9C4);
    }
}
