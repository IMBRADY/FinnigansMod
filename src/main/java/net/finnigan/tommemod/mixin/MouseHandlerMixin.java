package net.finnigan.tommemod.mixin;

import net.finnigan.tommemod.client.ConstructionRotationClientState;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses vanilla camera turning while the player is holding a construction banner and doing
 * shift + hold-right-click, so that gesture rotates the pending building instead of the camera (see
 * client.ConstructionRotationClientState for the actual suppression condition and rotation state -
 * kept out of this class deliberately, so this mixin's body stays a thin trigger and is easy to
 * reason about/debug if something goes wrong with it).
 * <p>
 * The suppression condition is checked fresh every single call, so there is no persistent
 * "stuck suppressed" state possible from this mixin's side - the moment
 * ConstructionRotationClientState.isSuppressingCameraTurn() goes false (item swapped, right-click
 * released, shift released, a GUI opened, etc.) turnPlayer() runs normally again on the very next
 * call, with a mouse delta of 0 (see below) so there is no snap/jump.
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {

    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void tommemod$suppressTurnWhileRotatingBanner(CallbackInfo ci) {
        if (!ConstructionRotationClientState.isSuppressingCameraTurn()) {
            return;
        }

        MouseHandlerAccessor accessor = (MouseHandlerAccessor) (Object) this;
        double dx = accessor.tommemod$getAccumulatedDX();

        ConstructionRotationClientState.onSuppressedDelta(dx);

        // Zero both accumulators (not just X) so no leftover delta gets applied by vanilla the
        // instant suppression ends on a later call - this is what prevents a camera "jump".
        accessor.tommemod$setAccumulatedDX(0.0);
        accessor.tommemod$setAccumulatedDY(0.0);

        ci.cancel();
    }
}
