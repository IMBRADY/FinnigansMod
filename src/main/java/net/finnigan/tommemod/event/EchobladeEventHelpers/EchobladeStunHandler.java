package net.finnigan.tommemod.event.EchobladeEventHelpers;

import net.finnigan.tommemod.TommeMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks entities stunned by Echoblade's sonic beam via a UUID-with-expiry map (same idiom as
 * ArackopeshItem's SPIDER_EXPIRY / ArackopeshSpiderEvents). Each server tick, every tracked entity
 * whose expiry hasn't passed yet has its horizontal deltaMovement zeroed (gravity/Y is preserved,
 * matching the codebase's existing "stay put, but still respect gravity" idiom) and its navigation
 * halted. This is a simpler, safer approximation of "stun" than touching AI/goal internals directly —
 * vanilla has no native stun concept.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class EchobladeStunHandler {

    private static final Map<UUID, Long> STUNNED_UNTIL_TICK = new ConcurrentHashMap<>();

    public static void stun(Level level, LivingEntity target, int durationTicks) {
        long expiry = level.getGameTime() + durationTicks;
        STUNNED_UNTIL_TICK.merge(target.getUUID(), expiry, Math::max);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (STUNNED_UNTIL_TICK.isEmpty()) return;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        long now = server.overworld().getGameTime();

        Iterator<Map.Entry<UUID, Long>> iterator = STUNNED_UNTIL_TICK.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Long> entry = iterator.next();
            if (now >= entry.getValue()) {
                iterator.remove();
                continue;
            }

            UUID uuid = entry.getKey();
            for (var level : server.getAllLevels()) {
                Entity entity = level.getEntity(uuid);
                if (entity instanceof LivingEntity living) {
                    living.setDeltaMovement(0, living.getDeltaMovement().y, 0);
                    living.hasImpulse = true;
                    if (living instanceof Mob mob) {
                        mob.getNavigation().stop();
                    }
                    break;
                }
            }
        }
    }
}
