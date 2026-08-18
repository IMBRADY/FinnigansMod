package net.finnigan.tommemod.client;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.network.ModNetwork;
import net.finnigan.tommemod.network.packet.ClassAbilityPacket;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Sends the class ability keypress and nothing else.
 *
 * No check here for whether the player owns anything - which ability fires, and whether it is off
 * cooldown, are the server's to decide (see ClassAbilityPacket). A client-side gate would have to
 * duplicate three cooldown timers it does not hold, and would be wrong the moment they disagreed.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClassAbilityInputHandler {

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        if (!KeyBindings.CLASS_ABILITY.consumeClick()) return;

        ModNetwork.CHANNEL.sendToServer(new ClassAbilityPacket());
    }
}
