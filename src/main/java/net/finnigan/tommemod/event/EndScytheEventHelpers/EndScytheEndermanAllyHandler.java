package net.finnigan.tommemod.event.EndScytheEventHelpers;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.item.custom.EndScytheItem;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * End Scythe passive: nearby Endermen defend the holder the way tamed wolves defend their owner —
 * when a player holding End Scythe is attacked, nearby Endermen target the attacker. Simpler and more
 * robust than faking a persistent "tamed" relationship: it's re-derived fresh on every hit.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class EndScytheEndermanAllyHandler {

    private static final double ALLY_RADIUS = 20.0;

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (victim.level().isClientSide) return;
        if (!EndScytheItem.isHeldBy(victim)) return;

        Entity attackerEntity = event.getSource().getEntity();
        if (!(attackerEntity instanceof LivingEntity attacker)) return;

        List<EnderMan> nearbyEndermen = victim.level().getEntitiesOfClass(EnderMan.class,
                victim.getBoundingBox().inflate(ALLY_RADIUS));

        for (EnderMan enderman : nearbyEndermen) {
            enderman.setTarget(attacker);
        }
    }
}
