package net.finnigan.tommemod.event.CustodireGladioEventHelpers;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.item.custom.CustodireGladioHelpers.ShieldWallManager;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Drives Custodire Gladio's deployed shield walls, same registration shape as FireWaveTickHandler. */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class ShieldWallTickHandler {
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ShieldWallManager.tick();
    }
}
