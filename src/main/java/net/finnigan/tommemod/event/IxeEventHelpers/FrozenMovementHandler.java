package net.finnigan.tommemod.event.IxeEventHelpers;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.effect.ModMobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Ixe's Frozen debuff should actually root the target in place, not just deal tick damage. */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class FrozenMovementHandler {

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) return;
        if (!entity.hasEffect(ModMobEffects.FROZEN.get())) return;

        entity.setDeltaMovement(0, entity.getDeltaMovement().y, 0);
        entity.hasImpulse = false;
        if (entity instanceof Mob mob) {
            mob.getNavigation().stop();
        }
    }
}
