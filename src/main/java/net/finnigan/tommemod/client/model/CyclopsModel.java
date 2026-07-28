package net.finnigan.tommemod.client.model;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.entity.custom.CyclopsEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

public class CyclopsModel extends GeoModel<CyclopsEntity> {

    @Override
    public ResourceLocation getModelResource(CyclopsEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "geo/entity/cyclops.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(CyclopsEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "textures/entity/cyclops/cyclops.png");
    }

    @Override
    public ResourceLocation getAnimationResource(CyclopsEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "animations/entity/cyclops.animation.json");
    }

    @Override
    public void setCustomAnimations(CyclopsEntity animatable, long instanceId, AnimationState<CyclopsEntity> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);

        // The lunge animation needs full, unshared control of the head bone - don't fight it with look tracking.
        if (animatable.isLunging()) return;

        CoreGeoBone head = this.getAnimationProcessor().getBone("head");
        if (head != null) {
            EntityModelData entityData = animationState.getData(DataTickets.ENTITY_MODEL_DATA);
            head.setRotX(entityData.headPitch() * Mth.DEG_TO_RAD);
            head.setRotY(entityData.netHeadYaw() * Mth.DEG_TO_RAD);
        }
    }
}
