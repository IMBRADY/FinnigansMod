package net.finnigan.tommemod.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.finnigan.tommemod.entity.custom.GiantSwordEntity;
import net.finnigan.tommemod.item.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.renderer.texture.OverlayTexture;


public class GiantSwordRenderer extends EntityRenderer<GiantSwordEntity> {

    private static final float SCALE = 14.0F; // "giant" — tune until it looks right

    public GiantSwordRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(GiantSwordEntity entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-45)); // settled upside-down on impact

        poseStack.scale(SCALE, SCALE, SCALE);

        ItemStack stack = new ItemStack(ModItems.INVERTED_SWORD.get());
        Minecraft.getInstance().getItemRenderer().renderStatic(
                stack, ItemDisplayContext.FIXED, packedLight, OverlayTexture.NO_OVERLAY,
                poseStack, buffer, entity.level(), 0);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    public net.minecraft.resources.ResourceLocation getTextureLocation(GiantSwordEntity entity) {
        return net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/misc/white.png");
        // Renderer requires a texture override even when unused since ItemStack rendering supplies its own
    }
}