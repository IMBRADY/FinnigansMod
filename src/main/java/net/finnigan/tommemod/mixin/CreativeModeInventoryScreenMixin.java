package net.finnigan.tommemod.mixin;

import net.finnigan.tommemod.capability.accessory.AccessoryHandler;
import net.finnigan.tommemod.capability.accessory.ModCapabilities;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.items.SlotItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreativeModeInventoryScreen.ItemPickerMenu.class)
public abstract class CreativeModeInventoryScreenMixin {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void tommemod$addAccessorySlots(Player player, CallbackInfo ci) {
        AbstractContainerMenuAccessor accessor = (AbstractContainerMenuAccessor) this;
        player.getCapability(ModCapabilities.ACCESSORY_HANDLER).ifPresent(handler -> {
            accessor.tommemod$invokeAddSlot(new SlotItemHandler(handler, AccessoryHandler.SLOT_HEAD_ACCESSORY, 77, 8));
            accessor.tommemod$invokeAddSlot(new SlotItemHandler(handler, AccessoryHandler.SLOT_ELYTRA, 77, 26));
            accessor.tommemod$invokeAddSlot(new SlotItemHandler(handler, AccessoryHandler.SLOT_TOTEM_ACCESSORY, 77, 44));
        });
    }
}