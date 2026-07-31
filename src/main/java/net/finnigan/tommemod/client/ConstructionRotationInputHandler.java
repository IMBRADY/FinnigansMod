package net.finnigan.tommemod.client;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.network.ModNetwork;
import net.finnigan.tommemod.network.packet.RotateConstructionBannerPacket;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Detects the falling edge of ConstructionRotationClientState's suppression condition (shift +
 * right-click released while holding a construction banner) and sends the finally-chosen facing to
 * the server, where RotateConstructionBannerPacket stamps it onto the held banner's NBT for
 * ConstructionBannerBlock#setPlacedBy to read at actual placement time.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ConstructionRotationInputHandler {

    private static boolean wasSuppressing = false;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        boolean suppressing = ConstructionRotationClientState.isSuppressingCameraTurn();
        if (wasSuppressing && !suppressing) {
            ModNetwork.CHANNEL.sendToServer(
                    new RotateConstructionBannerPacket(ConstructionRotationClientState.getCurrentFacing()));
        }
        wasSuppressing = suppressing;
    }
}
