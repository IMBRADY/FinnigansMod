package net.finnigan.tommemod.mixin;

import net.finnigan.tommemod.capability.accessory.AccessoryHandler;
import net.finnigan.tommemod.capability.accessory.ModCapabilities;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraftforge.items.SlotItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryMenu.class)
public abstract class InventoryMenuMixin {

    @Inject(method = "<init>(Lnet/minecraft/world/entity/player/Inventory;ZLnet/minecraft/world/entity/player/Player;)V",
            at = @At("RETURN"))
    private void tommemod$addAccessorySlots(Inventory inv, boolean active, Player player, CallbackInfo ci) {
        AbstractContainerMenuAccessor accessor = (AbstractContainerMenuAccessor) this;
        player.getCapability(ModCapabilities.ACCESSORY_HANDLER).ifPresent(handler -> {
            accessor.tommemod$invokeAddSlot(new SlotItemHandler(handler, AccessoryHandler.SLOT_HEAD_ACCESSORY, 77, 8));
            accessor.tommemod$invokeAddSlot(new SlotItemHandler(handler, AccessoryHandler.SLOT_ELYTRA, 77, 26));
            accessor.tommemod$invokeAddSlot(new SlotItemHandler(handler, AccessoryHandler.SLOT_TOTEM_ACCESSORY, 77, 44));
        });
    }
}