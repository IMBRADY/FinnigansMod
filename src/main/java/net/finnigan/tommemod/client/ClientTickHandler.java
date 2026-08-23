package net.finnigan.tommemod.client;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.network.ConfirmKeyPacket;
import net.finnigan.tommemod.network.ModNetwork;
import net.finnigan.tommemod.network.packet.ReleaseLanternaUsePacket;
import net.finnigan.tommemod.item.custom.LanternaItem;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientTickHandler { // .FORGE file, handles stuff that happens every tick

    private static boolean wasDown = false;
    private static boolean lanternaReleaseSent = false;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        boolean isDown = KeyBindings.RELEASE_SOULS_CONFIRM.isDown();
        if (isDown != wasDown) {
            wasDown = isDown;
            ModNetwork.CHANNEL.sendToServer(new ConfirmKeyPacket(isDown));
        }

        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (player == null) return;
        boolean useDown = minecraft.options.keyUse.isDown();
        boolean needsRelease = player.getInventory().items.stream().anyMatch(stack ->
                stack.getItem() instanceof LanternaItem && stack.hasTag()
                        && stack.getTag().getBoolean(LanternaItem.NEEDS_RELEASE_TAG))
                || player.getInventory().offhand.stream().anyMatch(stack ->
                stack.getItem() instanceof LanternaItem && stack.hasTag()
                        && stack.getTag().getBoolean(LanternaItem.NEEDS_RELEASE_TAG));
        if (needsRelease && !useDown && !lanternaReleaseSent) {
            lanternaReleaseSent = true;
            ModNetwork.CHANNEL.sendToServer(new ReleaseLanternaUsePacket());
        } else if (!needsRelease || useDown) {
            lanternaReleaseSent = false;
        }
    }
}
