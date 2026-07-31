package net.finnigan.tommemod.event.LumapierEventHelpers;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.effect.ModMobEffects;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Purifying Light's immunity logic: cancels Poison/Wither/Nausea(Confusion) from ever being applied to
 * an entity that currently has ModMobEffects.PURIFYING_LIGHT active, via Forge's
 * MobEffectEvent.Applicable (confirmed present in this Forge version, @Event.HasResult). Setting the
 * result to DENY blocks the effect from applying, mirroring vanilla's own undead-vs-poison immunity
 * plumbing.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class PurifyingLightImmunityHandler {

    @SubscribeEvent
    public static void onMobEffectApplicable(MobEffectEvent.Applicable event) {
        MobEffect effect = event.getEffectInstance().getEffect();
        if (effect != MobEffects.POISON && effect != MobEffects.WITHER && effect != MobEffects.CONFUSION) {
            return;
        }

        LivingEntity entity = event.getEntity();
        if (!entity.hasEffect(ModMobEffects.PURIFYING_LIGHT.get())) return;

        event.setResult(Event.Result.DENY);
    }
}
