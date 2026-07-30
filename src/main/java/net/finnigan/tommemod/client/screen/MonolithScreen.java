package net.finnigan.tommemod.client.screen;

import com.mojang.blaze3d.platform.NativeImage;
import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.block.entity.MonolithBlockEntity;
import net.finnigan.tommemod.config.ModConfig;
import net.finnigan.tommemod.menu.MonolithMenu;
import net.finnigan.tommemod.network.ModNetwork;
import net.finnigan.tommemod.network.packet.MonolithUpgradePacket;
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

import java.util.List;

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
    private static final ResourceLocation TERRAIN_TEXTURE_LOCATION =
            new ResourceLocation(TommeMod.MOD_ID, "dynamic/monolith_minimap");

    private enum Tab { MINIMAP, UPGRADES }

    // Canvas stays a fixed pixel size regardless of zoom; zoom instead changes how many world
    // blocks that fixed canvas represents (see computePixelsPerBlock).
    private static final int CANVAS_RADIUS = 63;
    private static final int CANVAS_SIZE = CANVAS_RADIUS * 2 + 1;
    private static final int MIN_EFFECTIVE_RADIUS = 8;
    private static final int MAX_EFFECTIVE_RADIUS = 256;

    private static final float MIN_ZOOM = 0.2F;
    private static final float MAX_ZOOM = 3.0F;
    private static final float ZOOM_STEP = 0.2F;

    private Tab activeTab = Tab.MINIMAP;
    private Button upgradeButton;
    private float zoom = 1.0F;

    private DynamicTexture terrainTexture;
    private long lastTerrainScanGameTime = Long.MIN_VALUE;
    private float lastScannedZoom = Float.NaN;

    public MonolithScreen(MonolithMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = 200;
        this.imageHeight = 210;
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
        this.addRenderableWidget(Button.builder(Component.literal("Upgrades"), b -> activeTab = Tab.UPGRADES)
                .bounds(x + 68, y + 6, 64, 16).build());

        this.upgradeButton = this.addRenderableWidget(Button.builder(Component.literal("Upgrade"), b -> onUpgradeClicked())
                .bounds(x + 130, y + 60, 64, 16).build());
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
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void onUpgradeClicked() {
        MonolithBlockEntity be = menu.getBlockEntity();
        if (!be.hasVillage()) return;
        if (be.getFarmEfficiencyLevel() >= ModConfig.FARM_EFFICIENCY_MAX_LEVEL.get()) return;
        ModNetwork.CHANNEL.sendToServer(new MonolithUpgradePacket(be.getBlockPos()));
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
        upgradeButton.visible = activeTab == Tab.UPGRADES;
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        MonolithBlockEntity be = menu.getBlockEntity();
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
            case UPGRADES -> renderUpgradesTab(guiGraphics, be, x, y);
        }

        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    // ---- Tactical Minimap (+ Defensive Metrics folded into the bottom) ----

    /** How many screen pixels one world block occupies at the current zoom - <1 when zoomed out
     * (each pixel covers multiple blocks), >1 when zoomed in. */
    private double computePixelsPerBlock() {
        int baseRadius = Math.min(ModConfig.MONOLITH_MINIMAP_RADIUS_BLOCKS.get(), MAX_EFFECTIVE_RADIUS);
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
            ResourceLocation icon = marker.type() == MonolithBlockEntity.MarkerType.IRON_GOLEM ? IRON_GOLEM_FACE : VILLAGER_FACE;
            guiGraphics.blit(icon, mx, mz, 0, 0, 8, 8, 8, 8);
        }

        int infoY = mapY + CANVAS_SIZE + 8;
        guiGraphics.drawString(font, "Iron Golems: " + be.getIronGolemCount(), x + 10, infoY, 0xFFFFFFFF);
        guiGraphics.drawString(font, "Active Soldiers: " + be.getActiveWarriorCount(), x + 10, infoY + 12, 0xFFFFFFFF);
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

    // ---- Village Upgrades ----

    private void renderUpgradesTab(GuiGraphics guiGraphics, MonolithBlockEntity be, int x, int y) {
        int level = be.getFarmEfficiencyLevel();
        int maxLevel = ModConfig.FARM_EFFICIENCY_MAX_LEVEL.get();
        double percent = level * ModConfig.FARM_EFFICIENCY_PERCENT_PER_LEVEL.get() * 100.0;

        guiGraphics.drawString(font, "Farm Efficiency: Lv. " + level + "/" + maxLevel, x + 10, y + 10, 0xFFFFFFFF);
        guiGraphics.drawString(font, "Crops grow " + (int) percent + "% faster", x + 10, y + 22, 0xFFAAAAAA);

        if (level < maxLevel) {
            List<? extends Integer> costs = ModConfig.FARM_EFFICIENCY_UPGRADE_COST_EMERALDS.get();
            int cost = costs.isEmpty() ? 0 : costs.get(Math.min(level, costs.size() - 1));
            guiGraphics.drawString(font, "Next level: " + cost + " emeralds", x + 10, y + 34, 0xFF55FF55);
        } else {
            guiGraphics.drawString(font, "Max level reached", x + 10, y + 34, 0xFFFFAA00);
        }

        guiGraphics.fill(x + 10, y + 66, x + 190, y + 90, 0x60404040);
        guiGraphics.drawString(font, "Coming Soon", x + 16, y + 74, 0xFF808080);

        guiGraphics.fill(x + 10, y + 96, x + 190, y + 120, 0x60404040);
        guiGraphics.drawString(font, "Coming Soon", x + 16, y + 104, 0xFF808080);
    }
}
