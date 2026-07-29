package net.finnigan.tommemod.mixin;

import net.finnigan.tommemod.capability.accessory.AccessoryHandler;
import net.finnigan.tommemod.capability.accessory.AccessoryItems;
import net.finnigan.tommemod.capability.accessory.ModCapabilities;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InventoryMenu.class)
public abstract class InventoryMenuMixin {

    @Unique
    private Slot tommemod$totemSlot;

    @Inject(method = "<init>(Lnet/minecraft/world/entity/player/Inventory;ZLnet/minecraft/world/entity/player/Player;)V",
            at = @At("RETURN"))
    private void tommemod$addAccessorySlots(Inventory inv, boolean active, Player player, CallbackInfo ci) {
        AbstractContainerMenuAccessor accessor = (AbstractContainerMenuAccessor) this;
        player.getCapability(ModCapabilities.ACCESSORY_HANDLER).ifPresent(handler -> {
            accessor.tommemod$invokeAddSlot(new SlotItemHandler(handler, AccessoryHandler.SLOT_HEAD_ACCESSORY, 77, 8));
            accessor.tommemod$invokeAddSlot(new SlotItemHandler(handler, AccessoryHandler.SLOT_ELYTRA, 77, 26));
            tommemod$totemSlot = accessor.tommemod$invokeAddSlot(
                    new SlotItemHandler(handler, AccessoryHandler.SLOT_TOTEM_ACCESSORY, 77, 44));
        });
    }

    // Shift-clicking a totem anywhere in the player's inventory/hotbar sends it straight to the
    // totem accessory slot, mirroring vanilla's shift-click-to-armor-slot behavior.
    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void tommemod$quickMoveTotemToAccessorySlot(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
        if (tommemod$totemSlot == null) return;

        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        Slot sourceSlot = menu.slots.get(index);
        if (sourceSlot == tommemod$totemSlot || !sourceSlot.hasItem()) return;

        ItemStack sourceStack = sourceSlot.getItem();
        if (!AccessoryItems.isTotemAccessory(sourceStack)) return;
        if (tommemod$totemSlot.hasItem()) return; // occupied — leave it for the player to swap manually

        ItemStack originalStack = sourceStack.copy();
        boolean moved = ((AbstractContainerMenuAccessor) this).tommemod$invokeMoveItemStackTo(
                sourceStack, tommemod$totemSlot.index, tommemod$totemSlot.index + 1, false);

        if (moved) {
            if (sourceStack.isEmpty()) {
                sourceSlot.set(ItemStack.EMPTY);
            } else {
                sourceSlot.setChanged();
            }
            cir.setReturnValue(originalStack);
            cir.cancel();
        }
    }
}