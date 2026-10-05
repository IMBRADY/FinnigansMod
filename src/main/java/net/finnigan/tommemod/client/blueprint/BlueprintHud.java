package net.finnigan.tommemod.client.blueprint;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.client.KeyBindings;
import net.finnigan.tommemod.network.packet.SyncConstructionSitesPacket.SiteInfo;
import net.finnigan.tommemod.village.blueprint.Blueprint;
import net.finnigan.tommemod.village.blueprint.Blueprints;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * The blueprint mode HUD: the building list down the left, what the selected building costs and
 * whether it fits along the bottom, the village's constructions down the right, and a blueprint-blue
 * frame round the screen so it is always obvious which mode you are in. The spectator hotbar is
 * hidden while it is up.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class BlueprintHud {

    private static final int PANEL = 0xC0101824;
    private static final int PANEL_EDGE = 0xFF3A6EA5;
    private static final int ACCENT = 0xFF8EC9FF;
    private static final int GOOD = 0xFF6FE07A;
    private static final int BAD = 0xFFFF6A5E;
    private static final int MUTED = 0xFF9AA7B4;
    private static final int GOLD = 0xFFFFD27F;
    private static final int ROW = 18;

    private BlueprintHud() {
    }

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Pre event) {
        if (!BlueprintClient.isActive()) return;
        var id = event.getOverlay().id();
        if (id.equals(VanillaGuiOverlay.HOTBAR.id()) || id.equals(VanillaGuiOverlay.CROSSHAIR.id())
                || id.equals(VanillaGuiOverlay.ITEM_NAME.id()) || id.equals(VanillaGuiOverlay.EXPERIENCE_BAR.id())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!BlueprintClient.isActive()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        GuiGraphics g = event.getGuiGraphics();
        int w = g.guiWidth();
        int h = g.guiHeight();
        float partial = event.getPartialTick();

        float frame = switch (BlueprintClient.phase()) {
            case ENTERING -> BlueprintClient.glideProgress(partial);
            case EXITING -> 1F - BlueprintClient.glideProgress(partial);
            case AWAITING_EXIT -> 0F;
            default -> 1F;
        };
        drawFrame(g, w, h, frame);
        if (!BlueprintClient.isPlanning()) return;

        Font font = mc.font;
        drawTitle(g, font, w);
        drawCatalog(g, font, h);
        drawSites(g, font, w);
        int controlsWidth = drawControls(g, font, w, h);
        drawSelection(g, font, w, h, controlsWidth);
        drawCrosshair(g, w, h);
        drawFlash(g, font, w, h);
    }

    private static void drawFrame(GuiGraphics g, int w, int h, float strength) {
        if (strength <= 0.01F) return;
        int edge = (int) (0x50 * strength) << 24 | 0x1E5AA0;
        int band = 18;
        g.fillGradient(0, 0, w, band, edge, 0x001E5AA0);
        g.fillGradient(0, h - band, w, h, 0x001E5AA0, edge);
        // Thin corner ticks, like the registration marks on a drawing.
        int tick = (int) (0xC0 * strength) << 24 | 0x8EC9FF;
        int len = 14;
        g.fill(4, 4, 4 + len, 5, tick);
        g.fill(4, 4, 5, 4 + len, tick);
        g.fill(w - 4 - len, 4, w - 4, 5, tick);
        g.fill(w - 5, 4, w - 4, 4 + len, tick);
        g.fill(4, h - 5, 4 + len, h - 4, tick);
        g.fill(4, h - 4 - len, 5, h - 4, tick);
        g.fill(w - 4 - len, h - 5, w - 4, h - 4, tick);
        g.fill(w - 5, h - 4 - len, w - 4, h - 4, tick);
    }

    private static void drawTitle(GuiGraphics g, Font font, int w) {
        String title = "BLUEPRINT MODE";
        String sub = BlueprintClient.builderCount() + " Builder" + (BlueprintClient.builderCount() == 1 ? "" : "s")
                + (BlueprintClient.isFree() ? "  ·  Creative: free" : "");
        int bw = Math.max(font.width(title), font.width(sub)) + 20;
        int x = (w - bw) / 2;
        panel(g, x, 6, bw, 26);
        g.drawCenteredString(font, title, w / 2, 9, ACCENT);
        g.drawCenteredString(font, sub, w / 2, 20, BlueprintClient.builderCount() > 0 ? MUTED : BAD);
    }

    private static void drawCatalog(GuiGraphics g, Font font, int h) {
        List<Blueprint> all = Blueprints.all();
        if (all.isEmpty()) return;
        int x = 8;
        int y = 40;
        int width = 132;
        int fit = Math.max(3, (h - y - 120) / ROW);
        int count = Math.min(all.size(), fit);
        int sel = BlueprintClient.selectedIndex();
        int first = Mth.clamp(sel - count / 2, 0, all.size() - count);

        panel(g, x, y, width, 14 + count * ROW + 4);
        g.drawString(font, "Buildings", x + 6, y + 4, GOLD);
        g.drawString(font, (sel + 1) + "/" + all.size(), x + width - 6 - font.width((sel + 1) + "/" + all.size()), y + 4, MUTED);

        for (int i = 0; i < count; i++) {
            int idx = first + i;
            Blueprint bp = all.get(idx);
            int rowY = y + 14 + i * ROW;
            boolean isSel = idx == sel;
            boolean usable = BlueprintClient.builderCount() >= bp.requiredBuilders() && BlueprintClient.canAfford(bp);
            if (isSel) {
                g.fill(x + 2, rowY, x + width - 2, rowY + ROW - 1, 0x803A6EA5);
                g.fill(x + 2, rowY, x + 3, rowY + ROW - 1, ACCENT);
            }
            g.renderItem(new ItemStack(bp.icon()), x + 5, rowY);
            String label = font.plainSubstrByWidth(bp.name(), width - 34);
            g.drawString(font, label, x + 24, rowY + 5, isSel ? 0xFFFFFFFF : usable ? 0xFFD8E2EC : MUTED);
            if (idx < 9) g.drawString(font, String.valueOf(idx + 1), x + width - 10, rowY + 5, 0xFF5D6B78);
        }
        if (first > 0) g.drawCenteredString(font, "▲", x + width / 2, y + 6, MUTED);
        if (first + count < all.size()) g.drawCenteredString(font, "▼", x + width / 2, y + 14 + count * ROW - 4, MUTED);
    }

    private static void drawSites(GuiGraphics g, Font font, int w) {
        List<SiteInfo> sites = BlueprintClient.sites();
        int width = 140;
        int x = w - width - 8;
        int y = 40;
        int rows = Math.max(1, sites.size());
        panel(g, x, y, width, 16 + rows * 24);
        g.drawString(font, "Under construction", x + 6, y + 4, GOLD);
        String cap = sites.size() + "/" + BlueprintClient.maxSites();
        g.drawString(font, cap, x + width - 6 - font.width(cap), y + 4, MUTED);
        if (sites.isEmpty()) {
            g.drawString(font, "Nothing yet", x + 6, y + 20, MUTED);
            return;
        }
        SiteInfo hovered = BlueprintClient.hoveredSite();
        for (int i = 0; i < sites.size(); i++) {
            SiteInfo s = sites.get(i);
            Blueprint bp = Blueprints.get(s.blueprintId());
            int rowY = y + 16 + i * 24;
            boolean isHovered = hovered != null && hovered.id().equals(s.id());
            if (isHovered) g.fill(x + 2, rowY - 1, x + width - 2, rowY + 22, 0x60FFFFFF);
            String name = bp != null ? bp.name() : s.blueprintId();
            g.drawString(font, font.plainSubstrByWidth(name, width - 40), x + 6, rowY + 1, 0xFFFFFFFF);
            String crew = s.workers() + "⚒";
            g.drawString(font, crew, x + width - 6 - font.width(crew), rowY + 1, s.workers() > 0 ? GOOD : BAD);
            float pct = s.total() > 0 ? (float) s.done() / s.total() : 1F;
            int barX = x + 6;
            int barW = width - 12;
            g.fill(barX, rowY + 12, barX + barW, rowY + 16, 0xFF2A3440);
            g.fill(barX, rowY + 12, barX + Math.round(barW * pct), rowY + 16, BlueprintClient.isCancelArmed(s.id()) ? BAD : GOOD);
        }
    }

    /** The key reference, in the bottom-right corner. Returns how wide it is so nothing overlaps it. */
    private static int drawControls(GuiGraphics g, Font font, int w, int h) {
        String[][] rows = {
                {"Scroll / 1-9", "Choose building"},
                {"E", "Catalog"},
                {key(KeyBindings.BLUEPRINT_ROTATE), "Rotate"},
                {key(KeyBindings.BLUEPRINT_RAISE) + " / " + key(KeyBindings.BLUEPRINT_LOWER), "Raise / lower"},
                {"Right click", "Lock in place"},
                {arrows(), "Nudge"},
                {"Left click / " + key(KeyBindings.BLUEPRINT_CONFIRM), "Build"},
                {key(KeyBindings.BLUEPRINT_CANCEL_SITE) + " (twice)", "Cancel site"},
                {"Esc / " + key(KeyBindings.BLUEPRINT_EXIT), "Unlock / leave"}
        };
        int keyWidth = 0;
        int actionWidth = 0;
        for (String[] r : rows) {
            keyWidth = Math.max(keyWidth, font.width(r[0]));
            actionWidth = Math.max(actionWidth, font.width(r[1]));
        }
        int width = keyWidth + actionWidth + 18;
        int height = rows.length * 10 + 16;
        int x = w - width - 8;
        int y = h - height - 8;
        panel(g, x, y, width, height);
        g.drawString(font, "Controls", x + 6, y + 4, GOLD);
        for (int i = 0; i < rows.length; i++) {
            int rowY = y + 15 + i * 10;
            g.drawString(font, rows[i][0], x + 6 + keyWidth - font.width(rows[i][0]), rowY, ACCENT);
            g.drawString(font, rows[i][1], x + 12 + keyWidth, rowY, 0xFFD8E2EC);
        }
        return width;
    }

    private static String arrows() {
        return key(KeyBindings.BLUEPRINT_NUDGE_FORWARD).equals("UP") ? "Arrow keys" : "Nudge keys";
    }

    private static void drawSelection(GuiGraphics g, Font font, int w, int h, int controlsWidth) {
        Blueprint bp = BlueprintClient.selectedBlueprint();
        if (bp == null) return;
        int width = Math.min(300, w - 300);
        width = Math.max(width, 220);
        // Centred, unless that would run into the controls in the corner.
        int x = Math.max(8, Math.min((w - width) / 2, w - controlsWidth - 16 - width));
        int height = 70;
        int y = h - height - 8;
        panel(g, x, y, width, height);

        g.renderItem(new ItemStack(bp.icon()), x + 6, y + 5);
        g.drawString(font, bp.name(), x + 26, y + 5, GOLD);
        String meta = bp.width(BlueprintClient.rotation()) + "×" + bp.depth(BlueprintClient.rotation()) + "×" + bp.height()
                + "  ·  " + facing() + (BlueprintClient.yOffset() != 0 ? "  ·  " + signed(BlueprintClient.yOffset()) + "y" : "");
        g.drawString(font, meta, x + width - 6 - font.width(meta), y + 5, MUTED);
        List<FormattedCharSequence> desc = font.split(Component.literal(bp.description()), width - 32);
        if (!desc.isEmpty()) g.drawString(font, desc.get(0), x + 26, y + 16, 0xFFC4CED8);

        // Cost, with what the player has.
        int cx = x + 6;
        int cy = y + 30;
        String builders = bp.requiredBuilders() + "⚒";
        boolean enoughBuilders = BlueprintClient.builderCount() >= bp.requiredBuilders();
        g.drawString(font, builders, cx, cy + 5, enoughBuilders ? GOOD : BAD);
        cx += font.width(builders) + 8;
        if (BlueprintClient.isFree()) {
            g.drawString(font, "Free (Creative)", cx, cy + 5, GOOD);
        } else {
            for (Blueprint.Cost c : bp.cost()) {
                if (cx > x + width - 40) break;
                g.renderItem(new ItemStack(c.item()), cx, cy);
                int have = BlueprintClient.countInInventory(c);
                String n = String.valueOf(c.count());
                g.drawString(font, n, cx + 17, cy + 5, have >= c.count() ? 0xFFFFFFFF : BAD);
                cx += 17 + font.width(n) + 6;
            }
        }

        Component problem = BlueprintClient.problem();
        Component status;
        int color;
        if (problem != null) {
            status = Component.literal("✖ ").append(problem);
            color = BAD;
        } else {
            status = Component.literal("✔ Ready - left click or Enter to build");
            color = GOOD;
        }
        g.drawString(font, font.plainSubstrByWidth(status.getString(), width - 12), x + 6, y + height - 13, color);
        if (BlueprintClient.isLocked()) {
            String locked = "LOCKED";
            g.drawString(font, locked, x + width - 6 - font.width(locked), y + height - 13, ACCENT);
        }
    }

    private static void drawCrosshair(GuiGraphics g, int w, int h) {
        int cx = w / 2;
        int cy = h / 2;
        int color = BlueprintClient.problem() == null ? 0xE0FFFFFF : 0xE0FF8A80;
        g.fill(cx - 5, cy, cx - 1, cy + 1, color);
        g.fill(cx + 2, cy, cx + 6, cy + 1, color);
        g.fill(cx, cy - 5, cx + 1, cy - 1, color);
        g.fill(cx, cy + 2, cx + 1, cy + 6, color);
    }

    private static void drawFlash(GuiGraphics g, Font font, int w, int h) {
        Component flash = BlueprintClient.flash();
        if (flash == null) return;
        int alpha = Mth.clamp(BlueprintClient.flashTicks() * 12, 0, 255);
        int color = (alpha << 24) | ((BlueprintClient.flashBad() ? BAD : GOOD) & 0xFFFFFF);
        int y = h - 70 - 8 - 16;
        int tw = font.width(flash) + 12;
        g.fill((w - tw) / 2, y - 3, (w + tw) / 2, y + 11, (Math.min(alpha, 0xC0) << 24) | 0x101824);
        g.drawCenteredString(font, flash, w / 2, y, color);
    }

    private static void panel(GuiGraphics g, int x, int y, int width, int height) {
        g.fill(x, y, x + width, y + height, PANEL);
        g.renderOutline(x, y, width, height, PANEL_EDGE);
    }

    private static String facing() {
        return switch (BlueprintClient.rotation()) {
            case CLOCKWISE_90 -> "faces west";
            case CLOCKWISE_180 -> "faces north";
            case COUNTERCLOCKWISE_90 -> "faces east";
            default -> "faces south";
        };
    }

    private static String signed(int n) {
        return n > 0 ? "+" + n : String.valueOf(n);
    }

    private static String key(net.minecraft.client.KeyMapping mapping) {
        return mapping.getTranslatedKeyMessage().getString().toUpperCase().replace("PAGE ", "Pg");
    }
}
