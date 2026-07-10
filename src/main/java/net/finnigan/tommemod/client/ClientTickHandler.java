package net.finnigan.tommemod.client;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.network.ConfirmKeyPacket;
import net.finnigan.tommemod.network.ModNetwork;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientTickHandler { // .FORGE file, handles stuff that happens every tick

    private static boolean wasDown = false;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        boolean isDown = KeyBindings.RELEASE_SOULS_CONFIRM.isDown();
        if (isDown != wasDown) {
            wasDown = isDown;
            ModNetwork.CHANNEL.sendToServer(new ConfirmKeyPacket(isDown));
        }
    }
}