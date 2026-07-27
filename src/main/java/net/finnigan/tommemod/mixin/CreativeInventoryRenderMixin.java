package net.finnigan.tommemod.mixin;

import net.finnigan.tommemod.client.ReputationBadgeRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeInventoryRenderMixin {

    @Shadow
    private static CreativeModeTab selectedTab;

    @Inject(method = "renderBg", at = @At("TAIL"))
    private void tommemod$renderAccessorySlotIcons(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY, CallbackInfo ci) {
        // same commented-out blit calls as InventoryScreenMixin, once accessory_slots.png has real art

        if (selectedTab.getType() != CreativeModeTab.Type.INVENTORY) return;
        CreativeModeInventoryScreen self = (CreativeModeInventoryScreen) (Object) this;
        int left = ((AbstractContainerScreen<?>) self).getGuiLeft();
        int top = ((AbstractContainerScreen<?>) self).getGuiTop();

        // Reputation badge in creative mode
        ReputationBadgeRenderer.renderBadge(guiGraphics, left + 128, top + 32);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void tommemod$renderReputationTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (selectedTab.getType() != CreativeModeTab.Type.INVENTORY) return;
        CreativeModeInventoryScreen self = (CreativeModeInventoryScreen) (Object) this;
        int left = ((AbstractContainerScreen<?>) self).getGuiLeft();
        int top = ((AbstractContainerScreen<?>) self).getGuiTop();
        ReputationBadgeRenderer.renderTooltipIfHovered(guiGraphics, left + 128, top + 32, mouseX, mouseY);
    }
}
