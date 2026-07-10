package net.finnigan.tommemod.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.finnigan.tommemod.TommeMod;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public class KeyBindings {

    public static final KeyMapping RELEASE_SOULS_CONFIRM = new KeyMapping(
            "key.tommemod.release_souls_confirm",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_T,
            "key.categories.tommemod"
    );

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(RELEASE_SOULS_CONFIRM);
    }
}