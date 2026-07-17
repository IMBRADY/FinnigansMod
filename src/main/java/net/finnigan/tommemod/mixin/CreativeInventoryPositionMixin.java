package net.finnigan.tommemod.mixin;

import net.finnigan.tommemod.capability.accessory.AccessoryHandler;
import net.finnigan.tommemod.capability.accessory.ModCapabilities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.items.SlotItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeInventoryPositionMixin {

    @Shadow private static CreativeModeTab selectedTab;

    @Inject(method = "selectTab", at = @At("TAIL"))
    private void tommemod$hideAccessorySlots(CreativeModeTab tab, CallbackInfo ci) {
        if (selectedTab.getType() != CreativeModeTab.Type.INVENTORY) return;

        AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) this;
        var slots = accessor.tommemod$getMenu().slots;
        int size = slots.size();
        if (size < 4) return;

        // remove in descending index order so earlier removals don't shift the positions
        // of the ones we still need to remove; leaves the trash slot (last one) untouched
        slots.remove(size - 2);
        slots.remove(size - 3);
        slots.remove(size - 4);
    }
}