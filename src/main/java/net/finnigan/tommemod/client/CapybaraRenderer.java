package net.finnigan.tommemod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.client.model.ButterflyModel;
import net.finnigan.tommemod.client.model.CapybaraModel;
import net.finnigan.tommemod.entity.custom.ButterflyEntity;
import net.finnigan.tommemod.entity.custom.CapybaraEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class CapybaraRenderer extends GeoEntityRenderer<CapybaraEntity> {
    public CapybaraRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new CapybaraModel());
        this.shadowRadius = 0.2f;
    }
    @Override
    public void render(CapybaraEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        if (entity.isBaby()) {
            poseStack.scale(0.8F, 0.8F, 0.8F);
            this.shadowRadius = 0.1f;
        } else {
            this.shadowRadius = 0.2f;
        }

        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }
}