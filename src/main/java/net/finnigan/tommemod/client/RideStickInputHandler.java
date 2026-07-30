package net.finnigan.tommemod.client;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.item.ModItems;
import net.finnigan.tommemod.network.ModNetwork;
import net.finnigan.tommemod.network.packet.AdjustRideDistancePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

// Scrolling while holding the Ride Stick adjusts how far away the grabbed mob floats, instead of
// switching hotbar slots like a normal scroll would.
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class RideStickInputHandler {

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        boolean holdingStick = player.getMainHandItem().getItem() == ModItems.RIDE_STICK.get()
                || player.getOffhandItem().getItem() == ModItems.RIDE_STICK.get();
        if (!holdingStick) return;

        event.setCanceled(true);
        ModNetwork.CHANNEL.sendToServer(new AdjustRideDistancePacket(event.getScrollDelta()));
    }
}
