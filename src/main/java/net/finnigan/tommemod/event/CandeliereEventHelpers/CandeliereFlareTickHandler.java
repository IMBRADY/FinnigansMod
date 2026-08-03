package net.finnigan.tommemod.event.CandeliereEventHelpers;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.item.custom.CandeliereHelpers.CandeliereFlareManager;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Advances Candeliere's in-flight flares, same registration shape as FireWaveTickHandler. */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class CandeliereFlareTickHandler {
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        CandeliereFlareManager.tick();
    }
}
