package net.finnigan.tommemod.client.blueprint;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.client.KeyBindings;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Every input blueprint mode takes over. The player is a vanilla spectator underneath, and
 * spectators already have uses for the mouse buttons (spectating mobs), the scroll wheel (fly speed),
 * the number keys and middle click (the teleport menu) - all of which are intercepted here before
 * vanilla sees them, so planning never accidentally hijacks the camera onto a cow.
 *
 * <pre>
 *  Left click              confirm placement      Right click      lock / unlock in place
 *  Left drag (walls)       lay a run of wall      X on a building  demolish it (twice), refunded
 *  Scroll / 1-9            choose building        Shift + scroll   rotate      Ctrl + scroll  raise/lower
 *  R                       rotate                 PgUp / PgDn      raise / lower
 *  Arrow keys              nudge (locks it)       X (twice)        cancel the site you are looking at
 *  E                       building catalog       Esc / B          unlock, or leave blueprint mode
 * </pre>
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class BlueprintInput {

    private static final KeyMapping[] BLUEPRINT_KEYS = {
            KeyBindings.BLUEPRINT_ROTATE, KeyBindings.BLUEPRINT_RAISE, KeyBindings.BLUEPRINT_LOWER,
            KeyBindings.BLUEPRINT_NUDGE_FORWARD, KeyBindings.BLUEPRINT_NUDGE_BACK, KeyBindings.BLUEPRINT_NUDGE_LEFT,
            KeyBindings.BLUEPRINT_NUDGE_RIGHT, KeyBindings.BLUEPRINT_CANCEL_SITE, KeyBindings.BLUEPRINT_EXIT
    };

    private BlueprintInput() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            BlueprintClient.tick();
            return;
        }
        // START runs before vanilla's own key handling this tick, so consuming here wins.
        if (!BlueprintClient.isActive()) {
            // Presses made in normal play must not pile up and all fire on entering the mode.
            drain(BLUEPRINT_KEYS);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;

        boolean planning = BlueprintClient.isPlanning();
        for (int i = 0; i < mc.options.keyHotbarSlots.length; i++) {
            while (mc.options.keyHotbarSlots[i].consumeClick()) {
                if (planning) BlueprintClient.select(i);
            }
        }
        while (mc.options.keyInventory.consumeClick()) {
            if (planning) mc.setScreen(new BlueprintCatalogScreen());
        }
        drain(mc.options.keyAttack, mc.options.keyUse, mc.options.keyPickItem, mc.options.keySwapOffhand, mc.options.keyDrop);

        while (KeyBindings.BLUEPRINT_ROTATE.consumeClick()) if (planning) BlueprintClient.rotate(Screen.hasShiftDown() ? 3 : 1);
        while (KeyBindings.BLUEPRINT_RAISE.consumeClick()) if (planning) BlueprintClient.raise(1);
        while (KeyBindings.BLUEPRINT_LOWER.consumeClick()) if (planning) BlueprintClient.raise(-1);
        while (KeyBindings.BLUEPRINT_NUDGE_FORWARD.consumeClick()) if (planning) nudgeOrRaise(1, 0);
        while (KeyBindings.BLUEPRINT_NUDGE_BACK.consumeClick()) if (planning) nudgeOrRaise(-1, 0);
        while (KeyBindings.BLUEPRINT_NUDGE_LEFT.consumeClick()) if (planning) BlueprintClient.nudge(0, -1);
        while (KeyBindings.BLUEPRINT_NUDGE_RIGHT.consumeClick()) if (planning) BlueprintClient.nudge(0, 1);
        while (KeyBindings.BLUEPRINT_CANCEL_SITE.consumeClick()) if (planning) BlueprintClient.cancelHoveredSite();
        while (KeyBindings.BLUEPRINT_EXIT.consumeClick()) if (planning) BlueprintClient.beginExit();
    }

    /** Shift + up/down raises and lowers instead, for keyboards without Page Up/Down. */
    private static void nudgeOrRaise(int forward, int right) {
        if (Screen.hasShiftDown()) BlueprintClient.raise(forward);
        else BlueprintClient.nudge(forward, right);
    }

    private static void drain(KeyMapping... keys) {
        for (KeyMapping key : keys) {
            while (key.consumeClick()) {
                // swallowed - see class comment
            }
        }
    }

    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (!BlueprintClient.isActive() || Minecraft.getInstance().screen != null) return;
        int button = event.getButton();
        if (button > GLFW.GLFW_MOUSE_BUTTON_MIDDLE) return;
        // Releases always go through. The right click that opened blueprint mode was pressed before
        // it started and is released after; swallowing that release left vanilla thinking the button
        // was still held, and its auto-repeat then "used" whatever the player looked at on the way out.
        // Left releases are still noticed, though: letting go is what lays a dragged run of wall.
        if (event.getAction() != GLFW.GLFW_PRESS) {
            if (event.getAction() == GLFW.GLFW_RELEASE && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) BlueprintClient.releasePrimary();
            return;
        }
        event.setCanceled(true);
        if (!BlueprintClient.isPlanning()) return;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) BlueprintClient.pressPrimary();
        else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) BlueprintClient.toggleLock();
    }

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        if (!BlueprintClient.isActive() || Minecraft.getInstance().screen != null) return;
        event.setCanceled(true);
        if (!BlueprintClient.isPlanning()) return;
        int dir = event.getScrollDelta() > 0 ? 1 : -1;
        if (Screen.hasControlDown()) BlueprintClient.raise(dir);
        else if (Screen.hasShiftDown()) BlueprintClient.rotate(dir > 0 ? 1 : 3);
        else BlueprintClient.cycle(-dir);
    }

    /**
     * Escape backs out of blueprint mode rather than pausing. Only while the window has focus,
     * though: losing focus also opens the pause screen, and that one should still pause the game.
     */
    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (!BlueprintClient.isActive() || !(event.getNewScreen() instanceof PauseScreen)) return;
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isWindowActive() || event.getCurrentScreen() != null) return;
        event.setCanceled(true);
        if (BlueprintClient.isPlanning()) BlueprintClient.back();
    }

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!BlueprintClient.isCameraLocked()) return;
        var input = event.getInput();
        input.forwardImpulse = 0;
        input.leftImpulse = 0;
        input.up = input.down = input.left = input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
    }
}
