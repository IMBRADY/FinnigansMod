package net.finnigan.tommemod.mixin;

import net.finnigan.tommemod.util.CooldownOwner;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tells a player's cooldown tracker who it belongs to, so
 * {@link net.finnigan.tommemod.mixin.ItemCooldownsMixin} has a player to ask about.
 *
 * At the tail of the constructor, because that is where the tracker exists and is reachable and where
 * nothing has had a chance to use it yet. Both sides construct their own, and both get stamped.
 */
@Mixin(Player.class)
public abstract class PlayerCooldownOwnerMixin {

    @Inject(method = "<init>", at = @At("TAIL"))
    private void tommemod$stampCooldownOwner(CallbackInfo callback) {
        Player self = (Player) (Object) this;
        if (self.getCooldowns() instanceof CooldownOwner owner) {
            owner.tommemod$setCooldownOwner(self);
        }
    }
}
