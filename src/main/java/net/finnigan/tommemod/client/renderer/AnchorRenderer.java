package net.finnigan.tommemod.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.entity.custom.UnhoistedTitanHelpers.AnchorEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Direct adaptation of ColletisVineRenderer's quad-strip-between-two-points technique, retextured
 * with anchorchain for both the chain body and the anchor head at the tip.
 */
public class AnchorRenderer extends EntityRenderer<AnchorEntity> {

    private static final ResourceLocation CHAIN_TEXTURE = new ResourceLocation(TommeMod.MOD_ID, "textures/entity/anchorchain.png");

    private static final float SEGMENT_LENGTH = 0.5F; // one texture tile per this many blocks
    private static final float CHAIN_WIDTH = 0.09F;

    public AnchorRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(AnchorEntity entity) {
        return CHAIN_TEXTURE;
    }

    @Override
    public void render(AnchorEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {

        if (!(entity.getOwner() instanceof Player owner)) {
            super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
            return;
        }

        // Entity's own interpolated render position — this is the (0,0,0) point of poseStack right now
        Vec3 entityRenderPos = entity.getPosition(partialTicks);

        Vec3 start = owner.getEyePosition(partialTicks).add(0, -0.2, 0).subtract(entityRenderPos);
        Vec3 end = entity.position().subtract(entityRenderPos);

        renderChain(poseStack, buffer, start, end);
        renderAnchorHead(poseStack, buffer, packedLight, end);

        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    public boolean shouldRender(AnchorEntity entity, net.minecraft.client.renderer.culling.Frustum frustum,
                                double camX, double camY, double camZ) {
        return true;
    }

    private void renderChain(PoseStack poseStack, MultiBufferSource buffer, Vec3 start, Vec3 end) {
        Vec3 diff = end.subtract(start);
        double length = diff.length();
        if (length < 0.01) return;

        Vec3 direction = diff.scale(1.0 / length);

        // Perpendicular vector so the chain quad faces roughly toward the camera axis
        Vec3 side = direction.cross(new Vec3(0, 1, 0)).normalize().scale(CHAIN_WIDTH);
        if (side.lengthSqr() < 0.0001) {
            side = new Vec3(CHAIN_WIDTH, 0, 0);
        }

        float vTiles = (float) (length / SEGMENT_LENGTH);

        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutout(CHAIN_TEXTURE));

        poseStack.pushPose();
        Matrix4f matrix = poseStack.last().pose();

        float x0 = (float) (start.x - side.x);
        float y0 = (float) (start.y - side.y);
        float z0 = (float) (start.z - side.z);
        float x1 = (float) (start.x + side.x);
        float y1 = (float) (start.y + side.y);
        float z1 = (float) (start.z + side.z);
        float x2 = (float) (end.x + side.x);
        float y2 = (float) (end.y + side.y);
        float z2 = (float) (end.z + side.z);
        float x3 = (float) (end.x - side.x);
        float y3 = (float) (end.y - side.y);
        float z3 = (float) (end.z - side.z);

        consumer.vertex(matrix, x0, y0, z0).color(255, 255, 255, 255).uv(0, 0).overlayCoords(0, 10).uv2(15728880).normal(0, 1, 0).endVertex();
        consumer.vertex(matrix, x1, y1, z1).color(255, 255, 255, 255).uv(1, 0).overlayCoords(0, 10).uv2(15728880).normal(0, 1, 0).endVertex();
        consumer.vertex(matrix, x2, y2, z2).color(255, 255, 255, 255).uv(1, vTiles).overlayCoords(0, 10).uv2(15728880).normal(0, 1, 0).endVertex();
        consumer.vertex(matrix, x3, y3, z3).color(255, 255, 255, 255).uv(0, vTiles).overlayCoords(0, 10).uv2(15728880).normal(0, 1, 0).endVertex();

        poseStack.popPose();
    }

    private void renderAnchorHead(PoseStack poseStack, MultiBufferSource buffer, int packedLight, Vec3 end) {
        poseStack.pushPose();
        poseStack.translate(end.x, end.y, end.z);
        poseStack.scale(0.4F, 0.4F, 0.4F);

        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutout(CHAIN_TEXTURE));
        Matrix4f matrix = poseStack.last().pose();

        consumer.vertex(matrix, -0.5F, -0.5F, 0).color(255, 255, 255, 255).uv(0, 1).overlayCoords(0, 10).uv2(packedLight).normal(0, 1, 0).endVertex();
        consumer.vertex(matrix, 0.5F, -0.5F, 0).color(255, 255, 255, 255).uv(1, 1).overlayCoords(0, 10).uv2(packedLight).normal(0, 1, 0).endVertex();
        consumer.vertex(matrix, 0.5F, 0.5F, 0).color(255, 255, 255, 255).uv(1, 0).overlayCoords(0, 10).uv2(packedLight).normal(0, 1, 0).endVertex();
        consumer.vertex(matrix, -0.5F, 0.5F, 0).color(255, 255, 255, 255).uv(0, 0).overlayCoords(0, 10).uv2(packedLight).normal(0, 1, 0).endVertex();

        poseStack.popPose();
    }
}
