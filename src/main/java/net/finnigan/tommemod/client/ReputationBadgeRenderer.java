package net.finnigan.tommemod.client;

import net.finnigan.tommemod.TommeMod;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared rendering logic for the reputation badge icon shown in the survival and creative
 * inventory screens (see InventoryScreenMixin / CreativeInventoryRenderMixin), reading from
 * ClientReputationHud. Kept as one place so both screens stay in sync.
 */
public class ReputationBadgeRenderer {

    private static final ResourceLocation BADGES = new ResourceLocation(TommeMod.MOD_ID, "textures/gui/reputation_badges.png");
    private static final int CELL = 16;
    private static final String[] TIER_NAMES = {"Novice", "Apprentice", "Journeyman", "Expert", "Master"};

    public static void renderBadge(GuiGraphics guiGraphics, int x, int y) {
        if (!ClientReputationHud.hasVillage()) return;
        int tier = Mth.clamp(ClientReputationHud.tierOrdinal(), 0, TIER_NAMES.length - 1);
        guiGraphics.blit(BADGES, x, y, tier * (float) CELL, 0F, CELL, CELL, CELL * TIER_NAMES.length, CELL);
    }

    public static void renderTooltipIfHovered(GuiGraphics guiGraphics, int x, int y, int mouseX, int mouseY) {
        if (!ClientReputationHud.hasVillage()) return;
        if (mouseX < x || mouseX >= x + CELL || mouseY < y || mouseY >= y + CELL) return;

        int tier = Mth.clamp(ClientReputationHud.tierOrdinal(), 0, TIER_NAMES.length - 1);
        int score = ClientReputationHud.score();
        int nextThreshold = ClientReputationHud.nextThreshold();

        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(TIER_NAMES[tier]).withStyle(ChatFormatting.BOLD));
        lines.add(Component.literal(ClientReputationHud.isChief() ? "Village Chief" : "Not a Village Chief")
                .withStyle(ChatFormatting.GRAY));
        if (nextThreshold < 0) {
            lines.add(Component.literal(score + " highest tier reputation").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            lines.add(Component.literal(score + " / " + nextThreshold + " reputation").withStyle(ChatFormatting.DARK_GRAY));
        }

        guiGraphics.renderComponentTooltip(Minecraft.getInstance().font, lines, mouseX, mouseY);
    }
}
