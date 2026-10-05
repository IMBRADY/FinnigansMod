package net.finnigan.tommemod.client.screen;

import com.mojang.blaze3d.platform.NativeImage;
import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.block.entity.MonolithBlockEntity;
import net.finnigan.tommemod.capability.reputation.ReputationTier;
import net.finnigan.tommemod.client.ClientReputationHud;
import net.finnigan.tommemod.config.ModConfig;
import net.finnigan.tommemod.menu.MonolithMenu;
import net.finnigan.tommemod.network.ModNetwork;
import net.finnigan.tommemod.network.packet.MonolithUpgradePacket;
import net.finnigan.tommemod.network.packet.SurveyBuildingsPacket;
import net.finnigan.tommemod.village.VillageTier;
import net.finnigan.tommemod.village.VillageUpgrade;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Fully custom Monolith GUI: two tabs (Tactical Minimap - with Defensive Metrics folded into its
 * bottom - and Village Upgrades) over live data mirrored from MonolithBlockEntity. Panel chrome is
 * hand-drawn (fill/outline) rather than a texture, since no art asset exists for this yet.
 *
 * The minimap's terrain is rendered into a cached GPU texture that's only rebuilt on the same
 * throttle as the server's data refresh (or immediately on a zoom change), and drawn with a single
 * blit per frame - issuing one guiGraphics.fill() call per terrain/perimeter pixel every frame
 * (the original approach) is what locked the game at ~5 FPS while the screen was open.
 */
public class MonolithScreen extends AbstractContainerScreen<MonolithMenu> {

    private static final ResourceLocation VILLAGER_FACE =
            new ResourceLocation(TommeMod.MOD_ID, "textures/gui/villager_face.png");
    private static final ResourceLocation IRON_GOLEM_FACE =
            new ResourceLocation(TommeMod.MOD_ID, "textures/gui/iron_golem_face.png");
    private static final ResourceLocation ELDER_FACE =
            new ResourceLocation(TommeMod.MOD_ID, "textures/gui/elder_face.png");
    private static final ResourceLocation WARRIOR_FACE =
            new ResourceLocation(TommeMod.MOD_ID, "textures/gui/warrior_face.png");
    private static final ResourceLocation TERRAIN_TEXTURE_LOCATION =
            new ResourceLocation(TommeMod.MOD_ID, "dynamic/monolith_minimap");

    private enum Tab { MINIMAP, TIER, UPGRADES, BUILDINGS }

    private static final int BUILDING_ROW_HEIGHT = 22;

    // Canvas stays a fixed pixel size regardless of zoom; zoom instead changes how many world
    // blocks that fixed canvas represents (see computePixelsPerBlock).
    private static final int CANVAS_RADIUS = 63;
    private static final int CANVAS_SIZE = CANVAS_RADIUS * 2 + 1;
    private static final int MIN_EFFECTIVE_RADIUS = 8;
    private static final int MAX_EFFECTIVE_RADIUS = 1024;

    private static final float MIN_ZOOM = 0.2F;
    private static final float MAX_ZOOM = 3.0F;
    private static final float ZOOM_STEP = 0.2F;

    /** Vertical room each upgrade's three lines of text and its button occupy. Tight enough that
     * four upgrades fit the panel without scrolling; adding a fifth means adding scrolling. */
    private static final int UPGRADE_ROW_HEIGHT = 54;
    /** Button sits under its upgrade's text rather than beside it - an effect line like
     * "Warriors have 75% more health" is long enough to run underneath a button placed alongside. */
    private static final int UPGRADE_BUTTON_OFFSET_Y = 34;

    private Tab activeTab = Tab.MINIMAP;
    /** The tier the Tier tab is showing; -1 until first opened, when it jumps to the next tier to buy. */
    private int viewedTier = -1;
    private final Button[] tierButtons = new Button[VillageTier.MAX];
    private Button tierUpgradeButton;
    private final Map<VillageUpgrade, Button> upgradeButtons = new EnumMap<>(VillageUpgrade.class);
    private Button surveyButton;
    private int buildingScroll;
    private float zoom = 1.0F;

    private DynamicTexture terrainTexture;
    private long lastTerrainScanGameTime = Long.MIN_VALUE;
    private float lastScannedZoom = Float.NaN;

    public MonolithScreen(MonolithMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = 240;
        // Sized by the Upgrades tab, which is the taller of the two: four rows of UPGRADE_ROW_HEIGHT
        // under a 30px header. The map tab has room to spare inside that.
        this.imageHeight = 276;
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // No player-inventory area on this screen; all labelling is drawn by the tabs themselves.
    }

    @Override
    protected void init() {
        super.init();
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;

        this.addRenderableWidget(Button.builder(Component.literal("Map"), b -> activeTab = Tab.MINIMAP)
                .bounds(x + 6, y + 6, 60, 16).build());
        this.addRenderableWidget(Button.builder(Component.literal("Tier"), b -> activeTab = Tab.TIER)
                .bounds(x + 68, y + 6, 48, 16).build());
        this.addRenderableWidget(Button.builder(Component.literal("Upgrades"), b -> activeTab = Tab.UPGRADES)
                .bounds(x + 118, y + 6, 56, 16).build());
        this.addRenderableWidget(Button.builder(Component.literal("Buildings"), b -> activeTab = Tab.BUILDINGS)
                .bounds(x + 176, y + 6, 58, 16).build());
        surveyButton = this.addRenderableWidget(Button.builder(Component.literal("Check buildings"),
                        b -> ModNetwork.CHANNEL.sendToServer(new SurveyBuildingsPacket(menu.getBlockEntity().getBlockPos())))
                .bounds(x + imageWidth - 110, y + imageHeight - 24, 100, 16).build());

        for (int i = 0; i < VillageTier.MAX; i++) {
            int tier = i + 1;
            tierButtons[i] = this.addRenderableWidget(Button.builder(Component.literal("Tier " + roman(tier)), b -> viewedTier = tier)
                    .bounds(x + 10 + i * 74, y + 46, 70, 16).build());
        }
        tierUpgradeButton = this.addRenderableWidget(Button.builder(Component.literal("Upgrade"), b -> onUpgradeClicked(VillageUpgrade.VILLAGE_TIER))
                .bounds(x + imageWidth - 90, y + imageHeight - 24, 80, 16).build());

        upgradeButtons.clear();
        int rowY = y + 30;
        for (VillageUpgrade upgrade : VillageUpgrade.values()) {
            // The tier has its own tab now.
            if (upgrade == VillageUpgrade.VILLAGE_TIER) continue;
            upgradeButtons.put(upgrade, this.addRenderableWidget(
                    Button.builder(Component.literal("Upgrade"), b -> onUpgradeClicked(upgrade))
                            .bounds(x + 10, rowY + UPGRADE_BUTTON_OFFSET_Y, 64, 16).build()));
            rowY += UPGRADE_ROW_HEIGHT;
        }
    }

    @Override
    public void removed() {
        super.removed();
        if (terrainTexture != null) {
            Minecraft.getInstance().getTextureManager().release(TERRAIN_TEXTURE_LOCATION);
            terrainTexture = null;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (activeTab == Tab.MINIMAP) {
            zoom = Mth.clamp((float) (zoom + delta * ZOOM_STEP), MIN_ZOOM, MAX_ZOOM);
            return true;
        }
        if (activeTab == Tab.BUILDINGS) {
            buildingScroll = Math.max(0, buildingScroll - (int) Math.signum(delta));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void onUpgradeClicked(VillageUpgrade upgrade) {
        MonolithBlockEntity be = menu.getBlockEntity();
        if (!be.hasVillage()) return;
        if (be.getUpgradeLevel(upgrade) >= upgrade.maxLevel()) return;
        ModNetwork.CHANNEL.sendToServer(new MonolithUpgradePacket(be.getBlockPos(), upgrade));
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        guiGraphics.fill(x, y, x + imageWidth, y + imageHeight, 0xE0202020);
        guiGraphics.renderOutline(x, y, imageWidth, imageHeight, 0xFF808080);
        guiGraphics.fill(x, y + 26, x + imageWidth, y + 27, 0xFF808080);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        MonolithBlockEntity be = menu.getBlockEntity();
        int currentTier = be.getUpgradeLevel(VillageUpgrade.VILLAGE_TIER);
        if (viewedTier < 1) viewedTier = Math.min(currentTier + 1, VillageTier.MAX);
        for (int i = 0; i < tierButtons.length; i++) {
            tierButtons[i].visible = activeTab == Tab.TIER && be.hasVillage();
            tierButtons[i].active = viewedTier != i + 1;
        }
        ReputationTier tierRequired = VillageTier.reputationRequiredFor(viewedTier);
        tierUpgradeButton.visible = activeTab == Tab.TIER && be.hasVillage() && viewedTier == currentTier + 1
                && (tierRequired == null || meetsStanding(tierRequired));
        tierUpgradeButton.setMessage(Component.literal("Unlock Tier " + roman(viewedTier)));

        upgradeButtons.forEach((upgrade, button) -> {
            // Nothing left to buy - or nothing this player is trusted with yet - reads better as no
            // button at all than as one that does nothing.
            int level = be.getUpgradeLevel(upgrade);
            ReputationTier required = upgrade.reputationRequiredFor(level + 1);
            button.visible = activeTab == Tab.UPGRADES
                    && be.hasVillage()
                    && level < upgrade.maxLevel()
                    && (required == null || meetsStanding(required));
        });
        surveyButton.visible = activeTab == Tab.BUILDINGS && be.hasVillage();
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int x = leftPos;
        int y = topPos + 30;

        if (!be.hasVillage()) {
            guiGraphics.drawCenteredString(font, "Not part of an established village",
                    leftPos + imageWidth / 2, y + 20, 0xFFAAAAAA);
            this.renderTooltip(guiGraphics, mouseX, mouseY);
            return;
        }

        switch (activeTab) {
            case MINIMAP -> renderMinimapTab(guiGraphics, be, x, y);
            case TIER -> renderTierTab(guiGraphics, be, x, y);
            case UPGRADES -> renderUpgradesTab(guiGraphics, be, x, y);
            case BUILDINGS -> renderBuildingsTab(guiGraphics, be, x, y);
        }

        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    // ---- Tactical Minimap (+ Defensive Metrics folded into the bottom) ----

    /** How many screen pixels one world block occupies at the current zoom - <1 when zoomed out
     * (each pixel covers multiple blocks), >1 when zoomed in. */
    private double computePixelsPerBlock() {
        // Observatories widen the map (see MonolithBlockEntity#getMapRadius).
        int baseRadius = Math.min(menu.getBlockEntity().getMapRadius(), MAX_EFFECTIVE_RADIUS);
        int effectiveRadius = Mth.clamp(Math.round(baseRadius / zoom), MIN_EFFECTIVE_RADIUS, MAX_EFFECTIVE_RADIUS);
        return (double) CANVAS_SIZE / (effectiveRadius * 2 + 1);
    }

    private void renderMinimapTab(GuiGraphics guiGraphics, MonolithBlockEntity be, int x, int y) {
        refreshTerrainTextureIfDue(be);

        double pixelsPerBlock = computePixelsPerBlock();
        int mapX = x + (imageWidth - CANVAS_SIZE) / 2;
        int mapY = y;

        if (terrainTexture != null) {
            guiGraphics.blit(TERRAIN_TEXTURE_LOCATION, mapX, mapY, 0, 0, CANVAS_SIZE, CANVAS_SIZE, CANVAS_SIZE, CANVAS_SIZE);
        }

        // Monolith's own position, dead center regardless of zoom
        guiGraphics.fill(mapX + CANVAS_RADIUS - 1, mapY + CANVAS_RADIUS - 1, mapX + CANVAS_RADIUS + 2, mapY + CANVAS_RADIUS + 2, 0xFFFFFFFF);

        for (MonolithBlockEntity.Marker marker : be.getMarkers()) {
            int mx = mapX + CANVAS_RADIUS + (int) Math.round(marker.dx() * pixelsPerBlock) - 4;
            int mz = mapY + CANVAS_RADIUS + (int) Math.round(marker.dz() * pixelsPerBlock) - 4;
            if (mx < mapX || mz < mapY || mx > mapX + CANVAS_SIZE - 8 || mz > mapY + CANVAS_SIZE - 8) continue;
            ResourceLocation icon = switch (marker.type()) {
                case IRON_GOLEM -> IRON_GOLEM_FACE;
                case ELDER -> ELDER_FACE;
                case WARRIOR -> WARRIOR_FACE;
                default -> VILLAGER_FACE;
            };
            guiGraphics.blit(icon, mx, mz, 0, 0, 8, 8, 8, 8);
        }

        int infoY = mapY + CANVAS_SIZE + 8;
        int iconX = x + 10;
        int textX = iconX + 10;
        guiGraphics.blit(IRON_GOLEM_FACE, iconX, infoY - 1, 0, 0, 8, 8, 8, 8);
        guiGraphics.drawString(font, "Iron Golems: " + be.getIronGolemCount(), textX, infoY, 0xFFFFFFFF);
        guiGraphics.blit(VILLAGER_FACE, iconX, infoY + 11, 0, 0, 8, 8, 8, 8);
        guiGraphics.drawString(font, "Active Soldiers: " + be.getActiveWarriorCount(), textX, infoY + 12, 0xFFFFFFFF);
        guiGraphics.blit(VILLAGER_FACE, iconX, infoY + 23, 0, 0, 8, 8, 8, 8);
        guiGraphics.drawString(font, "Total Population: " + be.getTotalPopulation(), textX, infoY + 24, 0xFFFFFFFF);
        if (be.getObservatoryCount() > 0) {
            guiGraphics.drawString(font, "Observatories: " + be.getObservatoryCount() + " (map radius " + be.getMapRadius() + ")",
                    textX, infoY + 36, 0xFF55FF55);
        }
    }

    /**
     * Rebuilds the cached terrain texture at most once per MONOLITH_REFRESH_INTERVAL_TICKS (or
     * immediately on a zoom change) - this is the only place that touches individual pixels; every
     * render() call after that just blits the already-uploaded GPU texture once.
     */
    private void refreshTerrainTextureIfDue(MonolithBlockEntity be) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;

        long now = level.getGameTime();
        boolean zoomChanged = zoom != lastScannedZoom;
        if (!zoomChanged && now - lastTerrainScanGameTime < ModConfig.MONOLITH_REFRESH_INTERVAL_TICKS.get()) {
            return;
        }
        lastTerrainScanGameTime = now;
        lastScannedZoom = zoom;

        double pixelsPerBlock = computePixelsPerBlock();
        int sampleSpan = Mth.clamp((int) Math.round(1.0 / pixelsPerBlock), 1, 2);
        BlockPos self = be.getBlockPos();

        // Pass 1: surface height per canvas pixel, needed up-front so shading can compare each
        // pixel to its north neighbor - this height-difference shading (not flat block color) is
        // what gives a real vanilla map its sense of terrain contour.
        int[] heights = new int[CANVAS_SIZE * CANVAS_SIZE];
        for (int pz = 0; pz < CANVAS_SIZE; pz++) {
            for (int px = 0; px < CANVAS_SIZE; px++) {
                int worldX = self.getX() + (int) Math.round((px - CANVAS_RADIUS) / pixelsPerBlock);
                int worldZ = self.getZ() + (int) Math.round((pz - CANVAS_RADIUS) / pixelsPerBlock);
                heights[px + pz * CANVAS_SIZE] = level.getHeight(Heightmap.Types.WORLD_SURFACE, worldX, worldZ) - 1;
            }
        }

        // Pass 2: base terrain color + height/fluid-depth shading + a fixed dither pattern - real
        // vanilla maps use this exact combination (see MapItemSavedData#update) to avoid looking
        // like flat single-tone blocks even on level ground.
        int[] colorsNative = new int[CANVAS_SIZE * CANVAS_SIZE];
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        for (int pz = 0; pz < CANVAS_SIZE; pz++) {
            int worldZ = self.getZ() + (int) Math.round((pz - CANVAS_RADIUS) / pixelsPerBlock);
            for (int px = 0; px < CANVAS_SIZE; px++) {
                int worldX = self.getX() + (int) Math.round((px - CANVAS_RADIUS) / pixelsPerBlock);
                int idx = px + pz * CANVAS_SIZE;
                int surfaceY = heights[idx];

                BlockPos surfacePos = new BlockPos(worldX, surfaceY, worldZ);
                var state = level.getBlockState(surfacePos);
                MapColor mapColor = state.getMapColor(level, surfacePos);

                int fluidDepth = 0;
                if (!state.getFluidState().isEmpty()) {
                    probe.set(worldX, surfaceY, worldZ);
                    while (fluidDepth < 32) {
                        probe.move(0, -1, 0);
                        if (level.getBlockState(probe).getFluidState().isEmpty()) break;
                        fluidDepth++;
                    }
                }

                int northHeight = pz > 0 ? heights[px + (pz - 1) * CANVAS_SIZE] : surfaceY;
                double heightScore = (surfaceY - northHeight) * 4.0 / (sampleSpan + 4);
                double fluidScore = -Math.min(fluidDepth, 10) * 0.1;
                double dither = (((px + pz) & 1) - 0.5) * 0.4;
                double shade = heightScore + fluidScore + dither;

                MapColor.Brightness brightness = shade > 0.6 ? MapColor.Brightness.HIGH
                        : shade < -0.6 ? MapColor.Brightness.LOW
                        : MapColor.Brightness.NORMAL;

                colorsNative[idx] = mapColor.calculateRGBColor(brightness);
            }
        }

        bakePoiBoundary(be, colorsNative, pixelsPerBlock);
        uploadTerrainTexture(colorsNative);
    }

    /**
     * A village's true footprint is the union of every claimed POI's small coverage radius (see
     * VillageManager#resolveVillage), not one big circumscribing circle - tracing the boundary of
     * that union (only where "inside" meets "outside") gives an outline that's actually centered
     * on and sized to the real village, and is irregular/non-circular for sprawling villages.
     */
    private void bakePoiBoundary(MonolithBlockEntity be, int[] colorsNative, double pixelsPerBlock) {
        // Walled: the walls are drawn in stone grey, and the edge of the ground they enclose in green.
        if (be.getOutlineColumns().length > 0) {
            plotColumns(be.getWallColumns(), colorsNative, pixelsPerBlock, 0xFF8C8C8C);
            plotColumns(be.getOutlineColumns(), colorsNative, pixelsPerBlock, 0xFF00FF00);
            return;
        }
        List<MonolithBlockEntity.PoiPoint> pois = be.getPoiPoints();
        if (pois.isEmpty()) return;

        double linkRadiusPixels = ModConfig.POI_LINK_RADIUS.get() * pixelsPerBlock;
        double radiusSq = linkRadiusPixels * linkRadiusPixels;
        boolean[] inside = new boolean[CANVAS_SIZE * CANVAS_SIZE];

        for (MonolithBlockEntity.PoiPoint poi : pois) {
            double centerPx = CANVAS_RADIUS + poi.dx() * pixelsPerBlock;
            double centerPz = CANVAS_RADIUS + poi.dz() * pixelsPerBlock;
            int minPx = Math.max(0, (int) Math.floor(centerPx - linkRadiusPixels));
            int maxPx = Math.min(CANVAS_SIZE - 1, (int) Math.ceil(centerPx + linkRadiusPixels));
            int minPz = Math.max(0, (int) Math.floor(centerPz - linkRadiusPixels));
            int maxPz = Math.min(CANVAS_SIZE - 1, (int) Math.ceil(centerPz + linkRadiusPixels));

            for (int pz = minPz; pz <= maxPz; pz++) {
                double dz = pz - centerPz;
                for (int px = minPx; px <= maxPx; px++) {
                    double dx = px - centerPx;
                    if (dx * dx + dz * dz <= radiusSq) {
                        inside[px + pz * CANVAS_SIZE] = true;
                    }
                }
            }
        }

        for (int pz = 0; pz < CANVAS_SIZE; pz++) {
            for (int px = 0; px < CANVAS_SIZE; px++) {
                int idx = px + pz * CANVAS_SIZE;
                if (!inside[idx]) continue;

                boolean edge = (px > 0 && !inside[idx - 1])
                        || (px < CANVAS_SIZE - 1 && !inside[idx + 1])
                        || (pz > 0 && !inside[idx - CANVAS_SIZE])
                        || (pz < CANVAS_SIZE - 1 && !inside[idx + CANVAS_SIZE]);

                if (edge) colorsNative[idx] = 0xFF00FF00;
            }
        }
    }

    private static void plotColumns(int[] columns, int[] colorsNative, double pixelsPerBlock, int color) {
        for (int packed : columns) {
            int px = CANVAS_RADIUS + (int) Math.round(MonolithBlockEntity.unpackDx(packed) * pixelsPerBlock);
            int pz = CANVAS_RADIUS + (int) Math.round(MonolithBlockEntity.unpackDz(packed) * pixelsPerBlock);
            if (px < 0 || pz < 0 || px >= CANVAS_SIZE || pz >= CANVAS_SIZE) continue;
            colorsNative[px + pz * CANVAS_SIZE] = color;
        }
    }

    private void uploadTerrainTexture(int[] colorsNative) {
        if (terrainTexture == null) {
            terrainTexture = new DynamicTexture(new NativeImage(NativeImage.Format.RGBA, CANVAS_SIZE, CANVAS_SIZE, false));
            Minecraft.getInstance().getTextureManager().register(TERRAIN_TEXTURE_LOCATION, terrainTexture);
        }

        NativeImage image = terrainTexture.getPixels();
        for (int pz = 0; pz < CANVAS_SIZE; pz++) {
            for (int px = 0; px < CANVAS_SIZE; px++) {
                // MapColor#calculateRGBColor already packs bytes as 0xAABBGGRR - NativeImage's
                // own native pixel format - so this goes in as-is. (Confirmed by decompiling
                // MapColor.class: it builds the int as alpha | b<<16 | g<<8 | r, not standard
                // ARGB. An earlier version of this code additionally "converted" ARGB->ABGR here,
                // which flipped the already-correct R and B channels - that was the bug behind
                // water rendering red and grass rendering blue.)
                image.setPixelRGBA(px, pz, colorsNative[px + pz * CANVAS_SIZE]);
            }
        }
        terrainTexture.upload();
    }

    // ---- Village Tier ----

    /**
     * The tier ladder: current tier at the top, a button per tier, and the chosen tier's perks one per
     * line. Whether it is already unlocked, the next to buy (with its price and standing requirement),
     * or further off is shown beside its name.
     */
    private void renderTierTab(GuiGraphics g, MonolithBlockEntity be, int x, int y) {
        int current = be.getUpgradeLevel(VillageUpgrade.VILLAGE_TIER);
        g.drawString(font, "Village Tier", x + 10, y + 2, 0xFFFFD27F);
        String now = current == 0 ? "None yet" : "Tier " + roman(current) + " of " + roman(VillageTier.MAX);
        g.drawString(font, now, x + imageWidth - 10 - font.width(now), y + 2, 0xFFFFFFFF);

        int tier = viewedTier;
        int top = y + 38;
        g.fill(x + 8, top, x + imageWidth - 8, y + imageHeight - 60, 0x40000000);
        String status;
        int statusColor;
        if (tier <= current) {
            status = "\u2714 Unlocked";
            statusColor = 0xFF55FF55;
        } else if (tier == current + 1) {
            status = "Next";
            statusColor = 0xFFFFAA00;
        } else {
            status = "Unlock Tier " + roman(tier - 1) + " first";
            statusColor = 0xFF888888;
        }
        g.drawString(font, "Tier " + roman(tier), x + 14, top + 5, 0xFFFFFFFF);
        g.drawString(font, status, x + imageWidth - 14 - font.width(status), top + 5, statusColor);

        int lineY = top + 20;
        int textX = x + 34;
        int textWidth = imageWidth - 34 - 14;
        boolean owned = tier <= current;
        for (VillageTier.Perk perk : VillageTier.perks(tier)) {
            List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.literal(perk.text()), textWidth);
            int blockHeight = Math.max(18, lines.size() * 10 + 2);
            g.renderItem(new net.minecraft.world.item.ItemStack(perk.icon()), x + 14, lineY + (blockHeight - 16) / 2);
            for (int i = 0; i < lines.size(); i++) {
                g.drawString(font, lines.get(i), textX, lineY + (blockHeight - lines.size() * 10) / 2 + i * 10 + 1,
                        owned ? 0xFFE6F0E6 : 0xFFAAAAAA);
            }
            lineY += blockHeight + 2;
        }

        int footY = y + imageHeight - 54;
        if (current >= VillageTier.MAX && tier == current) {
            g.drawString(font, "Highest tier reached", x + 10, footY, 0xFFFFAA00);
        } else if (tier == current + 1) {
            ReputationTier required = VillageTier.reputationRequiredFor(tier);
            if (required != null) {
                boolean ok = meetsStanding(required);
                g.drawString(font, (ok ? "\u2714 " : "\u2716 ") + "Requires " + titleCase(required.name()) + " standing",
                        x + 10, footY, ok ? 0xFF55FF55 : 0xFFFF5555);
            }
            g.drawString(font, "Cost: " + VillageUpgrade.VILLAGE_TIER.costOfNextLevel(current) + " emeralds",
                    x + 10, footY + 11, 0xFFFFFFFF);
        }
    }

    private static String roman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> String.valueOf(n);
        };
    }

    // ---- Buildings ----

    /**
     * Every finished building in the village and whether it is doing its job - standing buildings
     * with a purpose in green, ones knocked down below the line in red. "Check buildings" finds any
     * standing building the village has no record of (one finished before buildings were tracked).
     */
    private void renderBuildingsTab(GuiGraphics g, MonolithBlockEntity be, int x, int y) {
        List<MonolithBlockEntity.BuildingEntry> all = be.getBuildings();
        g.drawString(font, "Buildings", x + 10, y + 2, 0xFFFFD27F);
        long active = all.stream().filter(b -> b.hasPurpose() && b.standing()).count();
        String summary = active + " giving buffs";
        g.drawString(font, summary, x + imageWidth - 10 - font.width(summary), y + 2, 0xFF55FF55);

        int top = y + 16;
        int bottom = y + imageHeight - 64;
        int rows = Math.max(1, (bottom - top) / BUILDING_ROW_HEIGHT);
        buildingScroll = Math.min(buildingScroll, Math.max(0, all.size() - rows));
        if (all.isEmpty()) {
            g.drawString(font, "No finished buildings on record.", x + 10, top + 4, 0xFFAAAAAA);
            g.drawString(font, "Built one before they were tracked?", x + 10, top + 16, 0xFFAAAAAA);
            g.drawString(font, "Press Check buildings to find it.", x + 10, top + 28, 0xFFAAAAAA);
            return;
        }
        for (int i = 0; i < rows && buildingScroll + i < all.size(); i++) {
            MonolithBlockEntity.BuildingEntry b = all.get(buildingScroll + i);
            int rowY = top + i * BUILDING_ROW_HEIGHT;
            g.fill(x + 8, rowY, x + imageWidth - 8, rowY + BUILDING_ROW_HEIGHT - 2, 0x40000000);
            String status;
            int statusColor;
            if (!b.standing()) {
                status = "✖ Damaged";
                statusColor = 0xFFFF5555;
            } else if (b.hasPurpose()) {
                status = "✔ Active";
                statusColor = 0xFF55FF55;
            } else {
                status = "No buff";
                statusColor = 0xFF888888;
            }
            g.drawString(font, font.plainSubstrByWidth(b.name(), imageWidth - 90), x + 12, rowY + 2, 0xFFFFFFFF);
            g.drawString(font, status, x + imageWidth - 12 - font.width(status), rowY + 2, statusColor);
            if (!b.purposeText().isEmpty()) {
                g.drawString(font, font.plainSubstrByWidth(b.purposeText(), imageWidth - 24), x + 12, rowY + 11,
                        b.standing() ? 0xFFAAAAAA : 0xFF666666);
            }
        }
        if (all.size() > rows) {
            String more = (buildingScroll + 1) + "-" + Math.min(all.size(), buildingScroll + rows) + " of " + all.size() + " (scroll)";
            g.drawString(font, more, x + 10, bottom + 4, 0xFF888888);
        }
    }

    // ---- Village Upgrades ----

    private void renderUpgradesTab(GuiGraphics guiGraphics, MonolithBlockEntity be, int x, int y) {
        int rowY = y;
        for (VillageUpgrade upgrade : VillageUpgrade.values()) {
            if (upgrade == VillageUpgrade.VILLAGE_TIER) continue;
            int level = be.getUpgradeLevel(upgrade);
            int maxLevel = upgrade.maxLevel();

            guiGraphics.drawString(font, upgrade.displayName() + ": Lv. " + level + "/" + maxLevel,
                    x + 10, rowY + 2, 0xFFFFFFFF);
            guiGraphics.drawString(font, upgrade.effectDescription(level), x + 10, rowY + 13, 0xFFAAAAAA);

            if (level >= maxLevel) {
                guiGraphics.drawString(font, "Max level reached", x + 10, rowY + 23, 0xFFFFAA00);
            } else {
                ReputationTier required = upgrade.reputationRequiredFor(level + 1);
                // The requirement replaces the price rather than sitting beside it: until it is met
                // the price is not the thing standing in the way, and there is one line of room.
                if (required != null && !meetsStanding(required)) {
                    guiGraphics.drawString(font, "Requires " + titleCase(required.name()) + " standing",
                            x + 10, rowY + 23, 0xFFFF5555);
                } else {
                    guiGraphics.drawString(font,
                            "Next level: " + upgrade.costOfNextLevel(level) + " " + upgrade.costItemPlural(),
                            x + 10, rowY + 23, 0xFF55FF55);
                }
            }

            rowY += UPGRADE_ROW_HEIGHT;
        }
    }

    /**
     * Whether the local player is standing high enough with this village to buy an upgrade gated on
     * {@code required}. Read from the reputation HUD mirror, which tracks the player's nearest
     * village - and a player at this desk is inside the village the desk belongs to. The server
     * checks this again for real in MonolithUpgradePacket; this is only about explaining the button.
     */
    private static boolean meetsStanding(ReputationTier required) {
        return ClientReputationHud.hasVillage() && ClientReputationHud.tierOrdinal() >= required.ordinal();
    }

    private static String titleCase(String enumName) {
        return enumName.charAt(0) + enumName.substring(1).toLowerCase(Locale.ROOT);
    }
}
