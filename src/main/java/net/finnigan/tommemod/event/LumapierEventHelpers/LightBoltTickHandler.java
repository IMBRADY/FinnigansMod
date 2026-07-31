package net.finnigan.tommemod.event.LumapierEventHelpers;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.item.custom.LumapierHelpers.LightBoltManager;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Drives LightBoltManager's scheduled 5-shot spray steps, same registration shape as FireWaveTickHandler. */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class LightBoltTickHandler {
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        LightBoltManager.tick();
    }
}
