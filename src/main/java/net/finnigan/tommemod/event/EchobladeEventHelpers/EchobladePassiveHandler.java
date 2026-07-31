package net.finnigan.tommemod.event.EchobladeEventHelpers;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.item.custom.EchobladeItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Echoblade passive (server-side half): hostile mobs currently targeting a player holding Echoblade
 * lose that target once the mob's distance to the player exceeds 50% of the mob's own vanilla
 * Attributes.FOLLOW_RANGE. Approximates "reduced detection range" without touching vanilla
 * Sensor/NoiseListener internals. Mirrors ArackopeshEvents' "hostile mobs ignore you" precedent
 * (iterate entities, clear target based on a held-item condition).
 *
 * Footstep silencing is intentionally NOT implemented: there is no clean Forge-exposed hook to cancel
 * footstep sound events in 1.20.1, and the plan explicitly scopes this out as not worth the added
 * complexity for a first pass.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class EchobladePassiveHandler {

    private static final double DETECTION_RANGE_MULTIPLIER = 0.5;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        for (var level : server.getAllLevels()) {
            for (var entity : level.getAllEntities()) {
                if (!(entity instanceof Mob mob) || !(mob instanceof Enemy)) continue;

                LivingEntity target = mob.getTarget();
                if (!(target instanceof Player player)) continue;
                if (!EchobladeItem.isHeldBy(player)) continue;

                double followRange = mob.getAttributeValue(Attributes.FOLLOW_RANGE);
                double allowedRange = followRange * DETECTION_RANGE_MULTIPLIER;

                if (mob.distanceTo(player) > allowedRange) {
                    mob.setTarget(null);
                }
            }
        }
    }
}
