package net.finnigan.tommemod.client.blueprint;

import net.finnigan.tommemod.network.ModNetwork;
import net.finnigan.tommemod.network.packet.BlueprintStandActionPacket;
import net.finnigan.tommemod.network.packet.BlueprintStandStatusPacket;
import net.finnigan.tommemod.village.blueprint.Blueprints;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * What a Blueprint Stand shows when used: the village's Builders and every building under way with
 * its progress, refreshed every second while open, and the button into blueprint mode. Anyone can
 * read it; the button only works for the Chief.
 */
public class BlueprintStandScreen extends Screen {

    private static final int PANEL = 0xE0101824;
    private static final int EDGE = 0xFF3A6EA5;
    private static final int GOLD = 0xFFFFD27F;
    private static final int MUTED = 0xFF9AA7B4;
    private static final int GOOD = 0xFF6FE07A;
    private static final int BAD = 0xFFFF6A5E;
    private static final int ROW = 24;
    private static final int MAX_LISTED = 6;
    private static final int W = 240;

    private final BlockPos standPos;
    private BlueprintStandStatusPacket status;
    private Button enter;
    private int ticks;
    private int left;
    private int top;
    private int panelHeight;

    private BlueprintStandScreen(BlueprintStandStatusPacket status) {
        super(Component.literal("Blueprint Stand"));
        this.standPos = status.standPos;
        this.status = status;
    }

    public static void onStatus(BlueprintStandStatusPacket msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof BlueprintStandScreen open && open.standPos.equals(msg.standPos)) {
            open.status = msg;
            open.updateButton();
        } else if (msg.open) {
            mc.setScreen(new BlueprintStandScreen(msg));
        }
    }

    @Override
    protected void init() {
        panelHeight = 86 + MAX_LISTED * ROW + 30;
        left = (width - W) / 2;
        top = (height - panelHeight) / 2;
        enter = addRenderableWidget(Button.builder(Component.literal("Enter Blueprint Mode"), b -> {
                    ModNetwork.CHANNEL.sendToServer(new BlueprintStandActionPacket(standPos, BlueprintStandActionPacket.Action.ENTER));
                    onClose();
                })
                .bounds(left + 10, top + panelHeight - 28, W - 20, 20)
                .build());
        updateButton();
    }

    private void updateButton() {
        if (enter == null) return;
        enter.active = status.hasVillage && status.canPlan;
        enter.setTooltip(!status.hasVillage
                ? Tooltip.create(Component.literal("This stand isn't part of an established village"))
                : !status.canPlan ? Tooltip.create(Component.literal("Only the Village Chief may plan buildings")) : null);
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.distanceToSqr(standPos.getCenter()) > 8 * 8) {
            onClose();
            return;
        }
        if (++ticks % 20 == 0) {
            ModNetwork.CHANNEL.sendToServer(new BlueprintStandActionPacket(standPos, BlueprintStandActionPacket.Action.REFRESH));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.fill(left, top, left + W, top + panelHeight, PANEL);
        g.renderOutline(left, top, W, panelHeight, EDGE);
        g.drawString(font, "Blueprint Stand", left + 8, top + 8, GOLD);
        g.fill(left, top + 22, left + W, top + 23, EDGE);

        if (!status.hasVillage) {
            g.drawCenteredString(font, "Not part of an established village", left + W / 2, top + 50, MUTED);
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }

        g.drawString(font, "Builder Villagers: " + status.builders, left + 10, top + 30, status.builders > 0 ? GOOD : BAD);
        g.drawString(font, Blueprints.all().size() + " blueprints available", left + 10, top + 42, MUTED);
        if (status.builders == 0) {
            g.drawString(font, "Place more Blueprint Stands to employ Builders", left + 10, top + 54, MUTED);
        }
        String cap = status.sites.size() + "/" + status.maxSites;
        g.drawString(font, "Under construction", left + 10, top + 70, GOLD);
        g.drawString(font, cap, left + W - 10 - font.width(cap), top + 70, MUTED);

        List<BlueprintStandStatusPacket.Site> sites = status.sites;
        if (sites.isEmpty()) {
            g.drawString(font, "Nothing yet - enter blueprint mode to plan a building", left + 14, top + 86, 0xFF808080);
        }
        for (int i = 0; i < Math.min(sites.size(), MAX_LISTED); i++) {
            BlueprintStandStatusPacket.Site s = sites.get(i);
            int y = top + 84 + i * ROW;
            float pct = s.total() > 0 ? (float) s.done() / s.total() : 1F;
            g.fill(left + 10, y, left + W - 10, y + ROW - 4, 0x60303030);
            g.drawString(font, s.name(), left + 14, y + 3, 0xFFFFFFFF);
            String right = s.workers() + "⚒  " + Math.round(pct * 100) + "%";
            g.drawString(font, right, left + W - 14 - font.width(right), y + 3, s.workers() > 0 ? 0xFFCCCCCC : BAD);
            int barX = left + 14;
            int barW = W - 28;
            g.fill(barX, y + 13, barX + barW, y + 17, 0xFF2A3440);
            g.fill(barX, y + 13, barX + Math.round(barW * pct), y + 17, GOOD);
        }
        if (sites.size() > MAX_LISTED) {
            g.drawString(font, "+" + (sites.size() - MAX_LISTED) + " more", left + 14, top + 84 + MAX_LISTED * ROW - 2, 0xFF808080);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }
}
