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
            GLFW.GLFW_KEY_Y,
            "key.categories.tommemod"
    );

    /**
     * Opens the skill trees. Bound to the grave/backtick key, which vanilla leaves free and which sits
     * where a character sheet key usually does. Rebindable like anything else from Options > Controls.
     */
    public static final KeyMapping SKILL_TREE = new KeyMapping(
            "key.tommemod.skill_tree",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_GRAVE_ACCENT,
            "key.categories.tommemod"
    );

    /**
     * Fires whichever class ability the player owns - dash, ultimate or bond.
     *
     * One binding rather than three, because the classes are mutually exclusive: no player can ever
     * hold two of these at once, so three keys would be two dead keys each. Bound to V, which vanilla
     * leaves free.
     */
    public static final KeyMapping CLASS_ABILITY = new KeyMapping(
            "key.tommemod.class_ability",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            "key.categories.tommemod"
    );

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(RELEASE_SOULS_CONFIRM);
        event.register(SKILL_TREE);
        event.register(CLASS_ABILITY);
    }
}