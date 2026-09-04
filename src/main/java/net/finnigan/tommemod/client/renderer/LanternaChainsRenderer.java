package net.finnigan.tommemod.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.finnigan.tommemod.client.model.LanternaChainsModel;
import net.finnigan.tommemod.entity.custom.LanternaHelpers.LanternaChainsEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.LivingEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class LanternaChainsRenderer extends GeoEntityRenderer<LanternaChainsEntity> {
    public LanternaChainsRenderer(EntityRendererProvider.Context context) {
        super(context, new LanternaChainsModel());
        shadowRadius = 0.0F;
    }

    @Override
    public void render(LanternaChainsEntity entity, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        LivingEntity target = entity.getTarget();
        if (target == null) return;
        float horizontal = Math.max(0.45F, target.getBbWidth() + 0.25F);
        float vertical = Math.max(0.45F, target.getBbHeight() + 0.15F);
        poseStack.pushPose();
        poseStack.scale(horizontal, vertical, horizontal);
        super.render(entity, yaw, partialTick, poseStack, buffer, 15728880);
        poseStack.popPose();
    }

    @Override
    public boolean shouldRender(LanternaChainsEntity entity, net.minecraft.client.renderer.culling.Frustum frustum,
                                double x, double y, double z) {
        return true;
    }
}
