package net.finnigan.tommemod.client;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.client.screen.SkillTreeScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.finnigan.tommemod.skill.SkillTreeManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Opens the skill trees on the keybind.
 *
 * Entirely client-side: everything the screen draws - the definitions and the player's own progress -
 * is already on the client by the time they can press the key, so there is nothing to ask the server
 * for and no round trip before the screen appears.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class SkillTreeInputHandler {

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        if (!KeyBindings.SKILL_TREE.consumeClick()) return;

        // Only reachable if the server never sent any, which means either a datapack problem or a
        // server without the mod's data - worth saying so rather than opening an empty book.
        if (!SkillTreeManager.isLoaded()) {
            mc.player.displayClientMessage(
                    Component.literal("No skill trees are loaded.").withStyle(ChatFormatting.GRAY), true);
            return;
        }

        mc.setScreen(new SkillTreeScreen());
    }
}
