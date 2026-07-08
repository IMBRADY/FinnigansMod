package net.finnigan.tommemod.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.block.entity.OvenMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class OvenScreen extends AbstractContainerScreen<OvenMenu> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(TommeMod.MOD_ID, "textures/gui/oven_gui.png");

    // ---- Flame icon source rect, taken from the sprite sheet area outside the visible 176px GUI ----
    private static final int FLAME_SRC_X = 176;
    private static final int FLAME_SRC_Y = 0;
    private static final int FLAME_WIDTH = 14;
    private static final int FLAME_HEIGHT = 14;

    // ---- Where the flame renders on-screen, relative to the GUI's top-left corner ----
    private static final int FLAME_DEST_X = 58;
    private static final int FLAME_DEST_Y = 36;

    public OvenScreen(OvenMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;
        guiGraphics.blit(TEXTURE, x, y, 0, 0, imageWidth, imageHeight);

        renderFlame(guiGraphics, x, y);
    }

    private void renderFlame(GuiGraphics guiGraphics, int x, int y) {
        float fuelFraction = menu.blockEntity.getFuelTicksTotal() == 0 ? 0F
                : (float) menu.blockEntity.getFuelTicksLeft() / menu.blockEntity.getFuelTicksTotal();

        if (fuelFraction <= 0F) return; // fire's out — draw nothing

        int filledHeight = Math.max(1, (int) (FLAME_HEIGHT * fuelFraction));

        // Flame depletes from the top down, so the source Y and dest Y both shift
        // to only reveal the bottom portion as fuel runs low — same technique vanilla uses.
        guiGraphics.blit(TEXTURE,
                x + FLAME_DEST_X, y + FLAME_DEST_Y + (FLAME_HEIGHT - filledHeight),
                FLAME_SRC_X, FLAME_SRC_Y + (FLAME_HEIGHT - filledHeight),
                FLAME_WIDTH, filledHeight);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }
}