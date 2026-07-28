package net.finnigan.tommemod.client.model;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.entity.custom.HammerheadSharkEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

public class HammerheadSharkModel extends GeoModel<HammerheadSharkEntity> {

    @Override
    public ResourceLocation getModelResource(HammerheadSharkEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "geo/entity/hammerhead.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(HammerheadSharkEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "textures/entity/hammerhead/hammerhead.png");
    }

    @Override
    public ResourceLocation getAnimationResource(HammerheadSharkEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "animations/entity/hammerhead.animation.json");
    }

    // The "head" bone is an empty pivot (the actual jaw/head cubes are its children), so rotating it
    // here turns the whole head to follow the shark's current look direction - whether that's idly
    // looking around or bearing down on a chase target - layered on top of the swim/chase/bite clips.
    @Override
    public void setCustomAnimations(HammerheadSharkEntity animatable, long instanceId, AnimationState<HammerheadSharkEntity> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);

        CoreGeoBone head = this.getAnimationProcessor().getBone("head");
        if (head != null) {
            EntityModelData entityData = animationState.getData(DataTickets.ENTITY_MODEL_DATA);
            head.setRotX(entityData.headPitch() * Mth.DEG_TO_RAD);
            head.setRotY(entityData.netHeadYaw() * Mth.DEG_TO_RAD);
        }
    }
}
