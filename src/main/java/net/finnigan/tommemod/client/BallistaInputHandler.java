package net.finnigan.tommemod.client;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.entity.custom.BallistaEntity;
import net.finnigan.tommemod.network.ModNetwork;
import net.finnigan.tommemod.network.packet.BallistaFirePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Holding attack while manning a Ballista fires it.
 *
 * Client-side because the attack key is: the server is never told a key is down, only what was done
 * with it. Vanilla's own attack handling is no use here - it swings at whatever the crosshair is on,
 * and at sixty-four blocks that is nothing - so the key is read directly and turned into a request.
 * The request is sent every tick the key is held and the weapon does the rate-limiting, which is what
 * makes "hold to fire" come out as one bolt per reload rather than a stream.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class BallistaInputHandler {

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.screen != null) return;
        if (!mc.options.keyAttack.isDown()) return;
        if (!(player.getVehicle() instanceof BallistaEntity)) return;

        ModNetwork.CHANNEL.sendToServer(new BallistaFirePacket());
    }
}
