package net.finnigan.tommemod.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.entity.custom.EndScytheHelpers.EndScytheProjectileEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * Billboard-quad renderer for the End Scythe projectile: two crossed quads spinning fast around the
 * Y axis, same manual-VertexConsumer technique as IxeProjectileRenderer, just spun harder to read as
 * "spinning, fast" per the weapon's spec.
 */
public class EndScytheProjectileRenderer extends EntityRenderer<EndScytheProjectileEntity> {

    private static final ResourceLocation TEXTURE = new ResourceLocation(TommeMod.MOD_ID, "textures/entity/end_scythe_projectile.png");
    private static final float SCALE = 0.6F;

    public EndScytheProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(EndScytheProjectileEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(EndScytheProjectileEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
                        MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();

        float spin = (entity.tickCount + partialTicks) * 48.0F; // fast spin, per spec flavor
        poseStack.mulPose(Axis.YP.rotationDegrees(spin));
        poseStack.scale(SCALE, SCALE, SCALE);

        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutout(TEXTURE));
        Matrix4f matrix = poseStack.last().pose();

        quad(consumer, matrix, packedLight, 0F);
        quad(consumer, matrix, packedLight, 90F);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    private void quad(VertexConsumer consumer, Matrix4f matrix, int packedLight, float extraYaw) {
        float s = 1.0F;
        // two crossed quads to give the sprite volume from any viewing angle
        float rad = (float) Math.toRadians(extraYaw);
        float dx = (float) Math.sin(rad) * s;
        float dz = (float) Math.cos(rad) * s;

        consumer.vertex(matrix, -dx, -s, -dz).color(255, 255, 255, 255).uv(0, 1).overlayCoords(0, 10).uv2(packedLight).normal(0, 1, 0).endVertex();
        consumer.vertex(matrix, dx, -s, dz).color(255, 255, 255, 255).uv(1, 1).overlayCoords(0, 10).uv2(packedLight).normal(0, 1, 0).endVertex();
        consumer.vertex(matrix, dx, s, dz).color(255, 255, 255, 255).uv(1, 0).overlayCoords(0, 10).uv2(packedLight).normal(0, 1, 0).endVertex();
        consumer.vertex(matrix, -dx, s, -dz).color(255, 255, 255, 255).uv(0, 0).overlayCoords(0, 10).uv2(packedLight).normal(0, 1, 0).endVertex();
    }
}
