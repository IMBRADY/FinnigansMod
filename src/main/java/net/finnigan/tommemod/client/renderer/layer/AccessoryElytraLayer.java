package net.finnigan.tommemod.client.renderer.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.finnigan.tommemod.capability.accessory.AccessoryHandler;
import net.finnigan.tommemod.capability.accessory.ModCapabilities;
import net.minecraft.client.model.ElytraModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

public class AccessoryElytraLayer<T extends AbstractClientPlayer, M extends HumanoidModel<T>> extends RenderLayer<T, M> {

    private static final ResourceLocation TEXTURE = new ResourceLocation("textures/entity/elytra.png");
    private final ElytraModel<T> model;

    public AccessoryElytraLayer(RenderLayerParent<T, M> renderer, EntityModelSet modelSet) {
        super(renderer);
        this.model = new ElytraModel<>(modelSet.bakeLayer(ModelLayers.ELYTRA));
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T player,
                       float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks,
                       float netHeadYaw, float headPitch) {

        player.getCapability(ModCapabilities.ACCESSORY_HANDLER).ifPresent(handler -> {
            ItemStack elytra = handler.getStackInSlot(AccessoryHandler.SLOT_ELYTRA);
            ItemStack chestItem = player.getItemBySlot(EquipmentSlot.CHEST);

            if (elytra.isEmpty() || chestItem.getItem() instanceof net.minecraft.world.item.ElytraItem) return;

            poseStack.pushPose();
            poseStack.translate(0.0D, 0.0D, 0.125D);
            this.getParentModel().copyPropertiesTo(this.model);
            this.model.setupAnim(player, limbSwing, limbSwingAmount, partialTicks, netHeadYaw, headPitch);
            var vertexconsumer = buffer.getBuffer(RenderType.armorCutoutNoCull(TEXTURE));
            this.model.renderToBuffer(poseStack, vertexconsumer, packedLight, OverlayTexture.NO_OVERLAY,
                    1.0F, 1.0F, 1.0F, 1.0F);
            poseStack.popPose();
        });
    }
}