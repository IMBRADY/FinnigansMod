package net.finnigan.tommemod.client.renderer.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.finnigan.tommemod.capability.accessory.AccessoryHandler;
import net.finnigan.tommemod.capability.accessory.ModCapabilities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class AccessoryHeadLayer<T extends AbstractClientPlayer, M extends PlayerModel<T>> extends RenderLayer<T, M> {

    public AccessoryHeadLayer(RenderLayerParent<T, M> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T player,
                       float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks,
                       float netHeadYaw, float headPitch) {

        player.getCapability(ModCapabilities.ACCESSORY_HANDLER).ifPresent(handler -> {
            ItemStack stack = handler.getStackInSlot(AccessoryHandler.SLOT_HEAD_ACCESSORY);
            if (stack.isEmpty() || player.isInvisible()) return;

            poseStack.pushPose();
            // VERIFY: getParentModel() must be castable to HeadedModel (PlayerModel implements this in vanilla)
            ((HeadedModel) getParentModel()).getHead().translateAndRotate(poseStack);
            poseStack.translate(2.0D, -0.15D, 0.0D); // pushes it up above the skull rather than clipping into it — tweak to taste
            poseStack.scale(0.7F, -0.7F, 0.7F); // banners render "inside-out" by default; negative Y/Z flips it right-side-up above the head

            // VERIFY: renderStatic's exact overload/arg order in your ItemRenderer — this is the most likely spot to need adjustment
            Minecraft.getInstance().getItemRenderer().renderStatic(
                    stack, ItemDisplayContext.HEAD, packedLight,
                    net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                    poseStack, buffer, player.level(), 0);

            poseStack.popPose();
        });
    }
}