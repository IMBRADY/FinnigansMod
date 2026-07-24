package net.finnigan.tommemod.client;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.item.custom.AmethystCutlassItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID, value = Dist.CLIENT)
public class AmethystBeamSoundHandler {
    private static boolean playing = false;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        boolean using = player.isUsingItem() && player.getUseItem().getItem() instanceof AmethystCutlassItem;

        if (using && !playing) {
            mc.getSoundManager().play(new AmethystBeamSoundInstance(player));
            playing = true;
        } else if (!using && playing) {
            playing = false;
        }
    }
}