package net.finnigan.tommemod.event.WarFlammerEventHelpers;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.item.custom.WarFlammerHelpers.FireWaveManager;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Drives FireWaveManager's scheduled fire-wave steps, same registration shape as FireZoneTickHandler. */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class FireWaveTickHandler {
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        FireWaveManager.tick();
    }
}
