package net.finnigan.tommemod.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Granted by the Totem of Fasting while the wearer is at full hunger; worth +20% outgoing damage.
 * The damage itself is applied in event.TotemEffectEvents#onWellFedDamageBoost rather than through an
 * attribute modifier, so the bonus lands on every damage source the totem's old inline check covered
 * (projectiles, ability damage) instead of only on attribute-driven melee.
 */
public class WellFedEffect extends MobEffect {
    public WellFedEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xC98A2B);
    }
}
