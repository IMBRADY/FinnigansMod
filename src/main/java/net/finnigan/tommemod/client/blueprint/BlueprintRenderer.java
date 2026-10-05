package net.finnigan.tommemod.client.blueprint;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.network.packet.SyncConstructionSitesPacket.SiteInfo;
import net.finnigan.tommemod.village.blueprint.Blueprint;
import net.finnigan.tommemod.village.blueprint.BlueprintPlanner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Draws blueprint mode in the world: the building being placed as see-through copies of its real
 * blocks (blue when it can go there, red when it cannot), and every construction site's unbuilt
 * remainder as a fainter amber ghost, each boxed in an outline.
 *
 * <p>Ghost blocks are the real block models, just pushed through a translucent render type with
 * their colour and alpha scaled on the way into the buffer - see {@link TintedConsumer}.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class BlueprintRenderer {

    /** A very large building drawn as ghosts every frame gets expensive; past this, only the
     * outline and the first blocks are drawn. */
    private static final int MAX_GHOST_BLOCKS = 6000;

    private BlueprintRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (!BlueprintClient.isPlanning()) return;

        Minecraft mc = Minecraft.getInstance();
        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        BlockRenderDispatcher dispatcher = mc.getBlockRenderer();
        float time = (mc.level.getGameTime() + event.getPartialTick()) / 20F;
        // Fetched here, not held in a static: touching Sheets while Forge scans this class at mod
        // loading is too early for it.
        RenderType ghostType = Sheets.translucentCullBlockSheet();

        // Construction sites already under way.
        SiteInfo hovered = BlueprintClient.hoveredSite();
        for (SiteInfo site : BlueprintClient.sites()) {
            int drawn = 0;
            for (Blueprint.Cell cell : BlueprintClient.remainingFor(site)) {
                if (drawn++ >= MAX_GHOST_BLOCKS) break;
                ghost(pose, cam, buffers, dispatcher, ghostType, cell.pos(), cell.state(), 1.0F, 0.82F, 0.45F, 0.28F);
            }
            boolean isHovered = hovered != null && hovered.id().equals(site.id());
            boolean armed = BlueprintClient.isCancelArmed(site.id());
            float[] c = armed ? new float[]{1F, 0.25F, 0.2F} : isHovered ? new float[]{1F, 1F, 1F} : new float[]{1F, 0.75F, 0.2F};
            outline(pose, cam, buffers, BlueprintClient.boundsOf(site), c[0], c[1], c[2], isHovered ? 1F : 0.7F);
        }

        // The building being placed.
        BlueprintPlanner.Plan plan = BlueprintClient.plan();
        if (plan != null) {
            boolean ok = BlueprintClient.problem() == null;
            float pulse = 0.5F + 0.5F * (float) Math.sin(time * 4.0);
            float r = ok ? 0.55F : 1.0F;
            float g = ok ? 0.85F : 0.35F;
            float b = ok ? 1.0F : 0.35F;
            float a = 0.42F + 0.12F * pulse;
            int drawn = 0;
            for (BlueprintPlanner.Placement p : plan.placements()) {
                if (p.state().isAir()) continue;
                if (drawn++ >= MAX_GHOST_BLOCKS) break;
                ghost(pose, cam, buffers, dispatcher, ghostType, p.pos(), p.state(), r, g, b, a);
            }
            outline(pose, cam, buffers, plan.bounds(), r, g, b, BlueprintClient.isLocked() ? 1F : 0.6F);
        }

        buffers.endBatch(ghostType);
        buffers.endBatch(RenderType.lines());
    }

    private static void ghost(PoseStack pose, Vec3 cam, MultiBufferSource.BufferSource buffers, BlockRenderDispatcher dispatcher,
                              RenderType ghostType, BlockPos pos, BlockState state, float r, float g, float b, float a) {
        pose.pushPose();
        pose.translate(pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z);
        if (state.getRenderShape() == RenderShape.MODEL) {
            // Shrunk a hair about its centre so a ghost never z-fights the terrain it overlaps.
            pose.translate(0.5, 0.5, 0.5);
            pose.scale(0.998F, 0.998F, 0.998F);
            pose.translate(-0.5, -0.5, -0.5);
            MultiBufferSource tinted = type -> new TintedConsumer(buffers.getBuffer(type), r, g, b, a);
            dispatcher.renderSingleBlock(state, pose, tinted, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, ghostType);
        } else {
            // Chests, beds, water: no plain block model to ghost, so they are drawn as a small frame.
            LevelRenderer.renderLineBox(pose, buffers.getBuffer(RenderType.lines()), new AABB(0.15, 0.15, 0.15, 0.85, 0.85, 0.85), r, g, b, 1F);
        }
        pose.popPose();
    }

    private static void outline(PoseStack pose, Vec3 cam, MultiBufferSource.BufferSource buffers, BoundingBox box,
                                float r, float g, float b, float a) {
        AABB aabb = AABB.of(box).move(-cam.x, -cam.y, -cam.z).inflate(0.02);
        LevelRenderer.renderLineBox(pose, buffers.getBuffer(RenderType.lines()), aabb, r, g, b, a);
    }

    /** Passes vertices straight through, scaling each one's colour and alpha. */
    private record TintedConsumer(VertexConsumer delegate, float r, float g, float b, float a) implements VertexConsumer {
        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            delegate.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            delegate.color((int) (red * r), (int) (green * g), (int) (blue * b), (int) (alpha * a));
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            delegate.uv(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            delegate.overlayCoords(u, v);
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            delegate.uv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            delegate.normal(x, y, z);
            return this;
        }

        @Override
        public void endVertex() {
            delegate.endVertex();
        }

        @Override
        public void defaultColor(int red, int green, int blue, int alpha) {
            delegate.defaultColor(red, green, blue, alpha);
        }

        @Override
        public void unsetDefaultColor() {
            delegate.unsetDefaultColor();
        }
    }
}
