package net.finnigan.tommemod.client.blueprint;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.finnigan.tommemod.village.blueprint.Blueprint;
import net.finnigan.tommemod.village.blueprint.Blueprints;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;

import java.util.List;

/**
 * The building catalog, opened with the inventory key in blueprint mode: every blueprint down the
 * left, and for the highlighted one a slowly turning 3D model of the finished building with its
 * description, size, crew and materials. Choosing one returns to the world with it in hand.
 */
public class BlueprintCatalogScreen extends Screen {

    private static final int PANEL = 0xE0101824;
    private static final int EDGE = 0xFF3A6EA5;
    private static final int GOLD = 0xFFFFD27F;
    private static final int MUTED = 0xFF9AA7B4;
    private static final int GOOD = 0xFF6FE07A;
    private static final int BAD = 0xFFFF6A5E;
    private static final int ROW = 20;

    private int highlighted;
    private int scroll;
    private int left;
    private int top;
    private int listWidth;
    private int detailWidth;
    private int boxHeight;
    private int visibleRows;

    public BlueprintCatalogScreen() {
        super(Component.literal("Blueprints"));
        this.highlighted = BlueprintClient.selectedIndex();
    }

    @Override
    protected void init() {
        listWidth = 150;
        detailWidth = Math.min(300, width - listWidth - 40);
        boxHeight = Math.min(260, height - 30);
        left = (width - listWidth - detailWidth - 6) / 2;
        top = (height - boxHeight) / 2;
        visibleRows = (boxHeight - 22) / ROW;
        scroll = Mth.clamp(highlighted - visibleRows / 2, 0, Math.max(0, Blueprints.all().size() - visibleRows));

        int dx = left + listWidth + 6;
        addRenderableWidget(Button.builder(Component.literal("Plan this building"), b -> choose(highlighted))
                .bounds(dx + 8, top + boxHeight - 26, detailWidth - 16, 20)
                .build());
    }

    private void choose(int index) {
        BlueprintClient.select(index);
        onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int idx = rowAt(mouseX, mouseY);
        if (idx >= 0) {
            if (idx == highlighted && button == 0) {
                choose(idx);
            } else {
                highlighted = idx;
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int max = Math.max(0, Blueprints.all().size() - visibleRows);
        scroll = Mth.clamp(scroll - (int) Math.signum(delta), 0, max);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int size = Blueprints.all().size();
        if (size > 0) {
            if (keyCode == 264) { // down
                highlighted = Math.min(size - 1, highlighted + 1);
                keepVisible();
                return true;
            }
            if (keyCode == 265) { // up
                highlighted = Math.max(0, highlighted - 1);
                keepVisible();
                return true;
            }
            if (keyCode == 257 || keyCode == 335) { // enter
                choose(highlighted);
                return true;
            }
        }
        if (Minecraft.getInstance().options.keyInventory.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void keepVisible() {
        if (highlighted < scroll) scroll = highlighted;
        if (highlighted >= scroll + visibleRows) scroll = highlighted - visibleRows + 1;
    }

    private int rowAt(double mx, double my) {
        int listTop = top + 18;
        if (mx < left + 2 || mx > left + listWidth - 2 || my < listTop) return -1;
        int row = (int) ((my - listTop) / ROW);
        if (row >= visibleRows) return -1;
        int idx = scroll + row;
        return idx < Blueprints.all().size() ? idx : -1;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        List<Blueprint> all = Blueprints.all();

        // List
        g.fill(left, top, left + listWidth, top + boxHeight, PANEL);
        g.renderOutline(left, top, listWidth, boxHeight, EDGE);
        g.drawString(font, "Blueprints", left + 6, top + 6, GOLD);
        for (int i = 0; i < visibleRows && scroll + i < all.size(); i++) {
            int idx = scroll + i;
            Blueprint bp = all.get(idx);
            int y = top + 18 + i * ROW;
            boolean hovered = rowAt(mouseX, mouseY) == idx;
            if (idx == highlighted) g.fill(left + 2, y, left + listWidth - 2, y + ROW - 1, 0x903A6EA5);
            else if (hovered) g.fill(left + 2, y, left + listWidth - 2, y + ROW - 1, 0x40FFFFFF);
            g.renderItem(new ItemStack(bp.icon()), left + 5, y + 1);
            boolean usable = BlueprintClient.builderCount() >= bp.requiredBuilders() && BlueprintClient.canAfford(bp);
            g.drawString(font, font.plainSubstrByWidth(bp.name(), listWidth - 30), left + 25, y + 3, usable ? 0xFFFFFFFF : MUTED);
            g.drawString(font, bp.category(), left + 25, y + 11, 0xFF6C7A88);
        }

        // Detail
        int dx = left + listWidth + 6;
        g.fill(dx, top, dx + detailWidth, top + boxHeight, PANEL);
        g.renderOutline(dx, top, detailWidth, boxHeight, EDGE);
        if (!all.isEmpty()) {
            Blueprint bp = all.get(Mth.clamp(highlighted, 0, all.size() - 1));
            renderDetail(g, bp, dx, partialTick);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderDetail(GuiGraphics g, Blueprint bp, int dx, float partialTick) {
        g.drawString(font, bp.name(), dx + 8, top + 6, GOLD);
        String size = bp.width(Rotation.NONE) + "×" + bp.depth(Rotation.NONE) + "×" + bp.height();
        g.drawString(font, size, dx + detailWidth - 8 - font.width(size), top + 6, MUTED);

        int previewTop = top + 18;
        int previewHeight = Math.max(60, boxHeight - 118);
        g.fill(dx + 6, previewTop, dx + detailWidth - 6, previewTop + previewHeight, 0x60000000);
        g.enableScissor(dx + 6, previewTop, dx + detailWidth - 6, previewTop + previewHeight);
        renderModel(g, bp, dx + detailWidth / 2, previewTop + previewHeight / 2, Math.min(detailWidth - 12, previewHeight));
        g.disableScissor();

        int y = previewTop + previewHeight + 6;
        List<FormattedCharSequence> lines = font.split(Component.literal(bp.description()), detailWidth - 16);
        for (int i = 0; i < Math.min(3, lines.size()); i++) {
            g.drawString(font, lines.get(i), dx + 8, y + i * 10, 0xFFC4CED8);
        }
        y += 34;

        boolean enoughBuilders = BlueprintClient.builderCount() >= bp.requiredBuilders();
        g.drawString(font, "Builders: " + bp.requiredBuilders() + " (village has " + BlueprintClient.builderCount() + ")",
                dx + 8, y, enoughBuilders ? GOOD : BAD);
        g.drawString(font, bp.solidBlockCount() + " blocks", dx + detailWidth - 8 - font.width(bp.solidBlockCount() + " blocks"), y, MUTED);
        y += 12;

        int cx = dx + 8;
        if (BlueprintClient.isFree()) {
            g.drawString(font, "Free (Creative)", cx, y + 4, GOOD);
        } else {
            for (Blueprint.Cost c : bp.cost()) {
                if (cx > dx + detailWidth - 40) break;
                g.renderItem(new ItemStack(c.item()), cx, y);
                int have = BlueprintClient.countInInventory(c);
                String n = String.valueOf(c.count());
                g.drawString(font, n, cx + 17, y + 4, have >= c.count() ? 0xFFFFFFFF : BAD);
                cx += 17 + font.width(n) + 6;
            }
        }
    }

    /** The finished building, turning slowly, drawn block by block from its real models. */
    private void renderModel(GuiGraphics g, Blueprint bp, int cx, int cy, int size) {
        Minecraft mc = Minecraft.getInstance();
        BlockRenderDispatcher dispatcher = mc.getBlockRenderer();
        MultiBufferSource.BufferSource buffers = g.bufferSource();
        int w = bp.width(Rotation.NONE);
        int d = bp.depth(Rotation.NONE);
        int h = bp.height();
        float extent = (float) Math.sqrt(w * w + d * d + h * h);
        float scale = size / extent * 0.95F;
        float spin = (Util.getMillis() % 24000L) / 24000F * 360F;

        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(cx, cy, 200);
        pose.scale(scale, -scale, scale);
        pose.mulPose(Axis.XP.rotationDegrees(28F));
        pose.mulPose(Axis.YP.rotationDegrees(spin));
        pose.translate(-w / 2.0, -h / 2.0, -d / 2.0);
        Lighting.setupFor3DItems();
        for (Blueprint.Cell cell : bp.cells(Rotation.NONE)) {
            if (cell.state().isAir() || cell.state().getRenderShape() != RenderShape.MODEL) continue;
            pose.pushPose();
            pose.translate(cell.pos().getX(), cell.pos().getY(), cell.pos().getZ());
            dispatcher.renderSingleBlock(cell.state(), pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        buffers.endBatch();
        pose.popPose();
        Lighting.setupForFlatItems();
    }
}
