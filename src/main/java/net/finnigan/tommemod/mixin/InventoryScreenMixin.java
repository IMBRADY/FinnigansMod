package net.finnigan.tommemod.mixin;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.client.ReputationBadgeRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin {

    private static final ResourceLocation SLOT_ICONS =
            new ResourceLocation(TommeMod.MOD_ID, "textures/gui/accessory_slots.png");

    @Inject(method = "renderBg", at = @At("TAIL"))
    private void tommemod$renderAccessorySlots(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY, CallbackInfo ci) {
        InventoryScreen self = (InventoryScreen) (Object) this;
        int left = ((AbstractContainerScreen<?>) self).getGuiLeft();
        int top = ((AbstractContainerScreen<?>) self).getGuiTop();

        // guiGraphics.blit(SLOT_ICONS, left + 77, top + 8, 0, 0, 18, 18, 48, 18);   // hat/banner icon
        // guiGraphics.blit(SLOT_ICONS, left + 77, top + 26, 18, 0, 18, 18, 48, 18); // elytra icon
        // guiGraphics.blit(SLOT_ICONS, left + 77, top + 44, 36, 0, 18, 18, 48, 18); // totem icon
        // (blit calls commented out until accessory_slots.png actually exists with real icon art —
        //  uncomment once the texture is in place, since a missing file here would throw at runtime)

        // Reputation badge in survival mode
        ReputationBadgeRenderer.renderBadge(guiGraphics, left + 125, top + 62);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void tommemod$renderReputationTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        InventoryScreen self = (InventoryScreen) (Object) this;
        int left = ((AbstractContainerScreen<?>) self).getGuiLeft();
        int top = ((AbstractContainerScreen<?>) self).getGuiTop();
        // MUST MATCH ABOVE!
        ReputationBadgeRenderer.renderTooltipIfHovered(guiGraphics, left + 125, top + 62, mouseX, mouseY);
    }
}
