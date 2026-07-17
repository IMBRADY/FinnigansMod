package net.finnigan.tommemod.client.model;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.entity.custom.CapybaraEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

public class CapybaraModel extends GeoModel<CapybaraEntity> {
    @Override
    public ResourceLocation getModelResource(CapybaraEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "geo/entity/capybara.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(CapybaraEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "textures/entity/capybara.png");
    }

    @Override
    public ResourceLocation getAnimationResource(CapybaraEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "animations/entity/capybara.animation.json");
    }

    @Override
    public void setCustomAnimations(CapybaraEntity animatable, long instanceId, AnimationState<CapybaraEntity> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);

        // Retrieve the head bone from your Blockbench model design structure
        CoreGeoBone head = getAnimationProcessor().getBone("head");

        if (head != null) {
            // Fetch entity looking details (pitch/yaw) compiled by GeckoLib
            EntityModelData entityData = animationState.getData(DataTickets.ENTITY_MODEL_DATA);

            // Convert degrees to radians and apply them to the bone rotation matrix
            head.setRotX(entityData.headPitch() * ((float) Math.PI / 180F));
            head.setRotY(entityData.netHeadYaw() * ((float) Math.PI / 180F));
        }
    }
}