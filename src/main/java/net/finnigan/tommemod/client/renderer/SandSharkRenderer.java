package net.finnigan.tommemod.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.finnigan.tommemod.client.model.SandSharkModel;
import net.finnigan.tommemod.entity.custom.SandSharkEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Renders the sand shark sunk into the block it is standing on, so only its upper half breaks the
 * surface. This is purely visual - the hitbox stays where it is, so it fights and collides normally.
 */
public class SandSharkRenderer extends GeoEntityRenderer<SandSharkEntity> {

    public SandSharkRenderer(EntityRendererProvider.Context context) {
        super(context, new SandSharkModel());
        this.shadowRadius = 0.7f;
    }

    @Override
    public void render(SandSharkEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();
        poseStack.translate(0.0D, -SandSharkEntity.SUBMERGE_DEPTH, 0.0D);
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        poseStack.popPose();
    }
}
