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
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

@Mixin(Player.class)
public abstract class PlayerElytraMixin {

    private static final Logger TOMME_LOG = LogUtils.getLogger();

    @Inject(method = "tryToStartFallFlying", at = @At("HEAD"), cancellable = true)
    private void tommemod$allowAccessoryElytra(CallbackInfoReturnable<Boolean> cir) {
        Player self = (Player) (Object) this;
        TOMME_LOG.info("[tommemod] tryToStartFallFlying HEAD injection reached. side={}, onGround={}, isFallFlying={}, isInWater={}",
                self.level().isClientSide ? "CLIENT" : "SERVER",
                self.onGround(), self.isFallFlying(), self.isInWater());

        if (self.onGround() || self.isFallFlying() || self.isInWater() || self.hasEffect(MobEffects.LEVITATION)) {
            return;
        }

        boolean hasCap = self.getCapability(ModCapabilities.ACCESSORY_HANDLER).isPresent();
        TOMME_LOG.info("[tommemod] hasCapability={}", hasCap);

        self.getCapability(ModCapabilities.ACCESSORY_HANDLER).ifPresent(h -> {
            ItemStack elytra = h.getStackInSlot(AccessoryHandler.SLOT_ELYTRA);
            TOMME_LOG.info("[tommemod] elytra slot contents: {}, canElytraFly={}",
                    elytra.isEmpty() ? "EMPTY" : elytra.getItem(),
                    elytra.isEmpty() ? "n/a" : elytra.canElytraFly(self));

            if (!elytra.isEmpty() && elytra.canElytraFly(self)) {
                self.startFallFlying();
                cir.setReturnValue(true);
                cir.cancel();
                TOMME_LOG.info("[tommemod] override applied — startFallFlying() called, cancelled vanilla logic");
            }
        });
    }
}