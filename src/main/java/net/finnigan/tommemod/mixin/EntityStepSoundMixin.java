package net.finnigan.tommemod.mixin;

import net.finnigan.tommemod.item.custom.EchobladeItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Echoblade's passive: silent footsteps while holding it. Registered as a common (not client-only)
 * mixin since footstep sounds are triggered from both logical sides (server broadcasts the sound to
 * nearby players; the client also plays it locally for the moving player's own feedback) - silencing
 * only one side would leave the ability half-working. */
@Mixin(Entity.class)
public abstract class EntityStepSoundMixin {

    @Inject(method = "playStepSound", at = @At("HEAD"), cancellable = true)
    private void tommemod$silenceEchobladeFootsteps(BlockPos pos, BlockState state, CallbackInfo ci) {
        if ((Object) this instanceof Player player && EchobladeItem.isHeldBy(player)) {
            ci.cancel();
        }
    }
}
