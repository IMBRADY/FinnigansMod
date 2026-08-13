package net.finnigan.tommemod.mixin;

import net.finnigan.tommemod.item.custom.totems.TotemOfStickinessItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Totem of Stickiness: any wall the wearer is clinging to counts as climbable. What counts as clinging
 * lives in TotemOfStickinessItem.isClingingToWall, which the jump-off handler shares.
 *
 * Going through onClimbable rather than moving the player by hand means vanilla supplies all of the
 * behaviour for free and it matches ladders exactly - walking into the wall climbs, sneaking pins
 * you in place (isSuppressingSlidingDownLadder), letting go slides you down slowly, and fall damage
 * resets while attached.
 *
 * Runs on both sides: the accessory capability is synced to its owner, so the client reaches the
 * same verdict as the server and the player doesn't rubber-band off the wall.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityClimbableMixin {

    @Inject(method = "onClimbable", at = @At("HEAD"), cancellable = true)
    private void tommemod$stickToWalls(CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof Player player)) return;
        if (!TotemOfStickinessItem.isClingingToWall(player)) return;

        cir.setReturnValue(true);
    }
}
