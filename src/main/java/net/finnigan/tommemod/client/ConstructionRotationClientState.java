package net.finnigan.tommemod.client;

import net.finnigan.tommemod.block.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;

/**
 * Client-only state for "hold a construction banner, shift + hold right-click to rotate it instead
 * of turning the camera" (see mixin.MouseHandlerMixin, which is the thing that actually suppresses
 * the camera turn and feeds mouse deltas into {@link #onSuppressedDelta}, and
 * client.ConstructionRotationInputHandler, which detects release and sends the chosen facing to the
 * server). All logic here is plain state manipulation with no Mixin/engine-internals involved, kept
 * deliberately separate from the mixin itself so the mixin's own body can stay a thin trigger.
 * <p>
 * Rotation step -> Direction mapping here MUST stay in sync with
 * {@code village.BuildingStructures}'s own facing -> step mapping (south=0, west=1, north=2, east=3)
 * since both independently rotate the same conceptual footprint.
 */
public final class ConstructionRotationClientState {

    /** How many degrees of accumulated mouse-X delta advance the pending rotation by one 90-degree step. */
    private static final double DEGREES_PER_STEP = 40.0;

    private static double accumulatedDelta = 0.0;
    private static int rotationSteps = 0;

    private ConstructionRotationClientState() {
    }

    /** True exactly when camera turning should be suppressed in favor of rotating the pending building. */
    public static boolean isSuppressingCameraTurn() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return false;
        if (!isHoldingBanner(player)) return false;
        if (mc.options.keyUse == null || !mc.options.keyUse.isDown()) return false;
        return Screen.hasShiftDown();
    }

    private static boolean isHoldingBanner(LocalPlayer player) {
        return isBanner(player.getMainHandItem()) || isBanner(player.getOffhandItem());
    }

    private static boolean isBanner(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == ModBlocks.CONSTRUCTION_BANNER.get().asItem();
    }

    /** Called by the mixin every frame it suppresses turnPlayer(), with the raw horizontal cursor delta. */
    public static void onSuppressedDelta(double dx) {
        accumulatedDelta += dx;
        while (accumulatedDelta >= DEGREES_PER_STEP) {
            rotationSteps = (rotationSteps + 1) % 4;
            accumulatedDelta -= DEGREES_PER_STEP;
        }
        while (accumulatedDelta <= -DEGREES_PER_STEP) {
            rotationSteps = (rotationSteps + 3) % 4;
            accumulatedDelta += DEGREES_PER_STEP;
        }
    }

    /** south=0, west=1, north=2, east=3 - kept identical to BuildingStructures' facing->step mapping. */
    public static Direction getCurrentFacing() {
        return switch (rotationSteps) {
            case 1 -> Direction.WEST;
            case 2 -> Direction.NORTH;
            case 3 -> Direction.EAST;
            default -> Direction.SOUTH;
        };
    }
}
