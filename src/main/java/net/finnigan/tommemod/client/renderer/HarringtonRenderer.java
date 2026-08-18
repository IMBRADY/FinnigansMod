package net.finnigan.tommemod.client.renderer;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.entity.custom.HarringtonEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

/** Renders Harrington as one still, camera-facing PNG instead of a 3D model. */
public class HarringtonRenderer extends EntityRenderer<HarringtonEntity> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(TommeMod.MOD_ID, "textures/entity/harrington.png");

    // World-space size of the image. Adjust these after adding the artwork if desired.
    private static final float WIDTH = 1.0F;
    private static final float HEIGHT = 1.95F;

    public HarringtonRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(HarringtonEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        poseStack.translate(0.0D, HEIGHT / 2.0F, 0.0D);
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.scale(WIDTH, HEIGHT, WIDTH);

        PoseStack.Pose pose = poseStack.last();
        VertexConsumer vertices = buffer.getBuffer(RenderType.entityTranslucent(TEXTURE));
        vertices.vertex(pose.pose(), -0.5F, -0.5F, 0.0F).color(255, 255, 255, 255)
                .uv(0.0F, 1.0F).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight)
                .normal(pose.normal(), 0.0F, 1.0F, 0.0F).endVertex();
        vertices.vertex(pose.pose(), 0.5F, -0.5F, 0.0F).color(255, 255, 255, 255)
                .uv(1.0F, 1.0F).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight)
                .normal(pose.normal(), 0.0F, 1.0F, 0.0F).endVertex();
        vertices.vertex(pose.pose(), 0.5F, 0.5F, 0.0F).color(255, 255, 255, 255)
                .uv(1.0F, 0.0F).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight)
                .normal(pose.normal(), 0.0F, 1.0F, 0.0F).endVertex();
        vertices.vertex(pose.pose(), -0.5F, 0.5F, 0.0F).color(255, 255, 255, 255)
                .uv(0.0F, 0.0F).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight)
                .normal(pose.normal(), 0.0F, 1.0F, 0.0F).endVertex();
        poseStack.popPose();

        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(HarringtonEntity entity) {
        return TEXTURE;
    }
}
