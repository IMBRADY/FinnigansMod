package net.finnigan.tommemod.mixin;

import net.finnigan.tommemod.capability.accessory.AccessoryHandler;
import net.finnigan.tommemod.capability.accessory.ModCapabilities;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerElytraMixin {

    @Inject(method = "tryToStartFallFlying", at = @At("HEAD"), cancellable = true)
    private void tommemod$allowAccessoryElytra(CallbackInfoReturnable<Boolean> cir) {
        Player self = (Player) (Object) this;

        if (self.onGround() || self.isFallFlying() || self.isInWater() || self.hasEffect(MobEffects.LEVITATION)) {
            return; // let vanilla's own chest-slot check run normally
        }

        self.getCapability(ModCapabilities.ACCESSORY_HANDLER).ifPresent(h -> {
            ItemStack elytra = h.getStackInSlot(AccessoryHandler.SLOT_ELYTRA);
            if (!elytra.isEmpty() && elytra.canElytraFly(self)) {
                self.startFallFlying();
                cir.setReturnValue(true);
                cir.cancel();
            }
        });
    }
}