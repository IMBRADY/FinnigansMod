package net.finnigan.tommemod.mixin;

import net.finnigan.tommemod.capability.accessory.AccessoryHandler;
import net.finnigan.tommemod.capability.accessory.ModCapabilities;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.concurrent.atomic.AtomicReference;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerElytraMixin {

    @Redirect(method = "aiStep", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;getItemBySlot(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack tommemod$substituteAccessoryElytra(LocalPlayer self, EquipmentSlot slot) {
        ItemStack real = self.getItemBySlot(slot);

        if (slot == EquipmentSlot.CHEST && real.isEmpty()) {
            AtomicReference<ItemStack> result = new AtomicReference<>(real);
            self.getCapability(ModCapabilities.ACCESSORY_HANDLER).ifPresent(h -> {
                ItemStack accessoryElytra = h.getStackInSlot(AccessoryHandler.SLOT_ELYTRA);
                if (!accessoryElytra.isEmpty()) {
                    result.set(accessoryElytra);
                }
            });
            return result.get();
        }

        return real;
    }
}