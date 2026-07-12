package net.finnigan.tommemod.event.ArackopeshEventHelpers;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.item.custom.ArackopeshItem;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class ArackopeshSpiderEvents {

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        long now = server.overworld().getGameTime();
        Map<UUID, Long> expiryMap = ArackopeshItem.getSpiderExpiryMap();

        Iterator<Map.Entry<UUID, Long>> iterator = expiryMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Long> entry = iterator.next();
            if (now < entry.getValue()) continue;

            UUID spiderUuid = entry.getKey();
            for (var level : server.getAllLevels()) {
                Entity entity = level.getEntity(spiderUuid);
                if (entity != null) {
                    entity.discard();
                    break;
                }
            }
            iterator.remove();
        }
    }
}