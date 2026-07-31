package net.finnigan.tommemod.client.screen;

import net.finnigan.tommemod.block.entity.BuilderHubBlockEntity;
import net.finnigan.tommemod.menu.BuilderHubMenu;
import net.finnigan.tommemod.network.ModNetwork;
import net.finnigan.tommemod.network.packet.RequestBuildingBannerPacket;
import net.finnigan.tommemod.village.BuildingType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Fully custom Builder Hub GUI: one tile per BuildingType. House/Walls are real (cost + Builder
 * Villager requirement + a Build button); the rest render as the same gray "Coming Soon" tile
 * style already used by the Monolith's Village Upgrades tab.
 */
public class BuilderHubScreen extends AbstractContainerScreen<BuilderHubMenu> {

    private static final int TILE_HEIGHT = 42;

    public BuilderHubScreen(BuilderHubMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = 210;
        this.imageHeight = 30 + BuildingType.values().length * TILE_HEIGHT + 10;
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // Panel chrome/labels are all hand-drawn in render() instead.
    }

    @Override
    protected void init() {
        super.init();
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;

        BuildingType[] types = BuildingType.values();
        for (int i = 0; i < types.length; i++) {
            BuildingType type = types[i];
            if (!type.isImplemented()) continue;

            int tileY = y + 30 + i * TILE_HEIGHT;
            this.addRenderableWidget(Button.builder(Component.literal("Build"), b -> onBuildClicked(type))
                    .bounds(x + imageWidth - 56, tileY + 12, 46, 16)
                    .build());
        }
    }

    private void onBuildClicked(BuildingType type) {
        BuilderHubBlockEntity be = menu.getBlockEntity();
        ModNetwork.CHANNEL.sendToServer(new RequestBuildingBannerPacket(be.getBlockPos(), type));
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        guiGraphics.fill(x, y, x + imageWidth, y + imageHeight, 0xE0202020);
        guiGraphics.renderOutline(x, y, imageWidth, imageHeight, 0xFF808080);
        guiGraphics.fill(x, y + 26, x + imageWidth, y + 27, 0xFF808080);
        guiGraphics.drawString(font, "Builder Hub", x + 8, y + 10, 0xFFFFFFFF);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        BuilderHubBlockEntity be = menu.getBlockEntity();
        int x = leftPos;
        int y = topPos;

        if (!be.hasVillage()) {
            guiGraphics.drawCenteredString(font, "Not part of an established village",
                    leftPos + imageWidth / 2, y + 50, 0xFFAAAAAA);
            this.renderTooltip(guiGraphics, mouseX, mouseY);
            return;
        }

        BuildingType[] types = BuildingType.values();
        for (int i = 0; i < types.length; i++) {
            renderTile(guiGraphics, be, types[i], x, y + 30 + i * TILE_HEIGHT);
        }

        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    private void renderTile(GuiGraphics guiGraphics, BuilderHubBlockEntity be, BuildingType type, int x, int tileY) {
        int tileWidth = imageWidth - 16;
        int tileHeight = TILE_HEIGHT - 6;

        if (!type.isImplemented()) {
            guiGraphics.fill(x + 8, tileY, x + 8 + tileWidth, tileY + tileHeight, 0x60404040);
            guiGraphics.drawString(font, type.getDisplayName(), x + 14, tileY + 6, 0xFF808080);
            guiGraphics.drawString(font, "Coming Soon", x + 14, tileY + 18, 0xFF808080);
            return;
        }

        boolean hasBuilders = be.getBuilderVillagerCount() >= type.getRequiredBuilders();
        boolean canAfford = playerHas(Items.EMERALD, type.getEmeraldCost())
                && playerHas(type.getResourceItem(), type.getResourceCount());
        int titleColor = hasBuilders && canAfford ? 0xFFFFFFFF : 0xFFFF5555;

        guiGraphics.fill(x + 8, tileY, x + 8 + tileWidth, tileY + tileHeight, 0x60303030);
        guiGraphics.drawString(font, type.getDisplayName(), x + 14, tileY + 4, titleColor);
        guiGraphics.drawString(font,
                type.getEmeraldCost() + " emeralds + " + type.getResourceCount() + " " + type.getResourceItem().getDescription().getString(),
                x + 14, tileY + 16, 0xFFAAAAAA);
        guiGraphics.drawString(font,
                "Requires " + type.getRequiredBuilders() + " Builder Villagers (have " + be.getBuilderVillagerCount() + ")",
                x + 14, tileY + 27, hasBuilders ? 0xFF55FF55 : 0xFFFF5555);
    }

    private static boolean playerHas(Item item, int count) {
        if (count <= 0) return true;
        Player player = Minecraft.getInstance().player;
        if (player == null) return false;
        int available = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) available += stack.getCount();
        }
        return available >= count;
    }
}
