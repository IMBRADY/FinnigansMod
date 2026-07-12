package net.finnigan.tommemod.event.ArackopeshEventHelpers;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.item.custom.ArackopeshItem;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class ArackopeshEvents {

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        for (var level : server.getAllLevels()) {
            for (var entity : level.getAllEntities()) {
                if (entity instanceof Spider spider
                        && spider.getTarget() instanceof Player player
                        && ArackopeshItem.isHeldBy(player)) {
                    spider.setTarget(null);
                }
            }
        }
    }
}