package net.finnigan.tommemod.client.model;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.entity.custom.ScarecrowEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

public class ScarecrowModel extends GeoModel<ScarecrowEntity> {

    @Override
    public ResourceLocation getModelResource(ScarecrowEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "geo/entity/scarecrow.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(ScarecrowEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "textures/entity/scarecrow/scarecrow.png");
    }

    @Override
    public ResourceLocation getAnimationResource(ScarecrowEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "animations/entity/scarecrow.animation.json");
    }

    @Override
    public void setCustomAnimations(ScarecrowEntity animatable, long instanceId, AnimationState<ScarecrowEntity> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);

        // The spin animation needs full, unshared control of the head bone - don't fight it with look tracking.
        if (animatable.isSpinning()) return;

        CoreGeoBone head = this.getAnimationProcessor().getBone("head");
        if (head != null) {
            EntityModelData entityData = animationState.getData(DataTickets.ENTITY_MODEL_DATA);
            head.setRotX(entityData.headPitch() * Mth.DEG_TO_RAD);
            head.setRotY(entityData.netHeadYaw() * Mth.DEG_TO_RAD);
        }
    }
}
