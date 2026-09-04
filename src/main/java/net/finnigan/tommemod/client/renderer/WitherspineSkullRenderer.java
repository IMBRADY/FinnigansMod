package net.finnigan.tommemod.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.finnigan.tommemod.entity.custom.WitherspineHelpers.WitherspineSkullEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.WitherSkullRenderer;
import net.minecraft.world.entity.projectile.WitherSkull;

public class WitherspineSkullRenderer extends WitherSkullRenderer {
    public WitherspineSkullRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(WitherSkull skull, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight) {
        if (skull instanceof WitherspineSkullEntity && skull.tickCount + partialTick < 1.5F) {
            return;
        }
        super.render(skull, entityYaw, partialTick, poseStack, buffers, packedLight);
    }
}
