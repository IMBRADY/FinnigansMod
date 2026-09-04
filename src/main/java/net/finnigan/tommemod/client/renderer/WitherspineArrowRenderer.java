package net.finnigan.tommemod.client.renderer;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.finnigan.tommemod.entity.custom.WitherspineHelpers.WitherspineArrowEntity;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public class WitherspineArrowRenderer extends ArrowRenderer<WitherspineArrowEntity> {
    private static final ResourceLocation ARROW_TEXTURE =
            new ResourceLocation("minecraft", "textures/entity/projectiles/arrow.png");

    public WitherspineArrowRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(WitherspineArrowEntity arrow) {
        return ARROW_TEXTURE;
    }

    @Override
    public void vertex(Matrix4f pose, Matrix3f normal, VertexConsumer consumer,
                       int x, int y, int z, float u, float v,
                       int normalX, int normalZ, int normalY, int packedLight) {
        consumer.vertex(pose, x, y, z)
                .color(0, 0, 0, 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(packedLight)
                .normal(normal, normalX, normalY, normalZ)
                .endVertex();
    }
}
