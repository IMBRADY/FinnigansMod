package net.finnigan.tommemod.mixin;

import net.finnigan.tommemod.client.blueprint.BlueprintClient;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Holds the camera still while blueprint mode glides it up to its bird's-eye view or back down to
 * the player's feet - the glide sets yaw and pitch itself every tick, and mouse input fighting it
 * would make the move judder. The decision lives in {@link BlueprintClient#isCameraLocked()}; this is
 * only the trigger.
 * <p>
 * The condition is checked fresh every call, so nothing can stick: the moment the glide ends,
 * turnPlayer() runs normally again, and because both accumulators are zeroed while it is held there
 * is no leftover delta to snap the view when it is released.
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {

    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void tommemod$holdCameraDuringGlide(CallbackInfo ci) {
        if (!BlueprintClient.isCameraLocked()) {
            return;
        }

        MouseHandlerAccessor accessor = (MouseHandlerAccessor) (Object) this;
        accessor.tommemod$setAccumulatedDX(0.0);
        accessor.tommemod$setAccumulatedDY(0.0);
        ci.cancel();
    }
}
