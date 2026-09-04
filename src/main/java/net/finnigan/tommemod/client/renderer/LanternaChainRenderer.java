package net.finnigan.tommemod.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.entity.custom.LanternaHelpers.LanternaChainEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public class LanternaChainRenderer extends EntityRenderer<LanternaChainEntity> {
    private static final ResourceLocation CHAIN = LanternaChainEntity.GRAPPLE_SETTINGS.chainTexture();
    private static final ResourceLocation TIP = LanternaChainEntity.GRAPPLE_SETTINGS.tipTexture();

    public LanternaChainRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(LanternaChainEntity entity) {
        return TIP;
    }

    @Override
    public void render(LanternaChainEntity entity, float yaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        if (!(entity.getOwner() instanceof Player owner)) return;
        if (entity.hasArrived()) return;
        Vec3 origin = entity.getPosition(partialTicks);
        Vec3 start = owner.getEyePosition(partialTicks).add(0, -0.2, 0).subtract(origin);
        Vec3 end = entity.getPosition(partialTicks).subtract(origin);
        renderLine(poseStack, buffer, start, end);
        renderTip(poseStack, buffer, packedLight, end);
    }

    private void renderLine(PoseStack poseStack, MultiBufferSource buffer, Vec3 start, Vec3 end) {
        Vec3 difference = end.subtract(start);
        double length = difference.length();
        if (length < 0.01) return;
        Vec3 direction = difference.scale(1.0 / length);
        Vec3 side = direction.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 0.0001) side = new Vec3(1, 0, 0);
        side = side.normalize().scale(LanternaChainEntity.GRAPPLE_SETTINGS.chainWidth());
        float tiles = (float) (length / LanternaChainEntity.GRAPPLE_SETTINGS.segmentLength());
        VertexConsumer vertices = buffer.getBuffer(RenderType.entityCutout(CHAIN));
        Matrix4f matrix = poseStack.last().pose();
        vertex(vertices, matrix, start.subtract(side), 0, 0);
        vertex(vertices, matrix, start.add(side), 1, 0);
        vertex(vertices, matrix, end.add(side), 1, tiles);
        vertex(vertices, matrix, end.subtract(side), 0, tiles);
    }

    private void vertex(VertexConsumer vertices, Matrix4f matrix, Vec3 point, float u, float v) {
        vertices.vertex(matrix, (float) point.x, (float) point.y, (float) point.z)
                .color(255, 255, 255, 255).uv(u, v).overlayCoords(0, 10).uv2(15728880)
                .normal(0, 1, 0).endVertex();
    }

    private void renderTip(PoseStack poseStack, MultiBufferSource buffer, int light, Vec3 end) {
        poseStack.pushPose();
        poseStack.translate(end.x, end.y, end.z);
        poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        poseStack.mulPose(Axis.ZP.rotationDegrees(LanternaChainEntity.GRAPPLE_SETTINGS.tipRotationDegrees()));
        float tipScale = LanternaChainEntity.GRAPPLE_SETTINGS.tipScale();
        poseStack.scale(tipScale, tipScale, tipScale);
        VertexConsumer vertices = buffer.getBuffer(RenderType.entityCutout(TIP));
        Matrix4f matrix = poseStack.last().pose();
        tipVertex(vertices, matrix, -0.5F, -0.5F, 0, 1, light);
        tipVertex(vertices, matrix, 0.5F, -0.5F, 1, 1, light);
        tipVertex(vertices, matrix, 0.5F, 0.5F, 1, 0, light);
        tipVertex(vertices, matrix, -0.5F, 0.5F, 0, 0, light);
        poseStack.popPose();
    }

    private void tipVertex(VertexConsumer vertices, Matrix4f matrix, float x, float y, float u, float v, int light) {
        vertices.vertex(matrix, x, y, 0).color(255, 255, 255, 255).uv(u, v)
                .overlayCoords(0, 10).uv2(light).normal(0, 1, 0).endVertex();
    }

    @Override
    public boolean shouldRender(LanternaChainEntity entity, net.minecraft.client.renderer.culling.Frustum frustum,
                                double x, double y, double z) {
        return true;
    }
}
