package net.finnigan.tommemod.client.model;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.entity.custom.LivingArmorEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

public class LivingArmorModel extends GeoModel<LivingArmorEntity> {

    @Override
    public ResourceLocation getModelResource(LivingArmorEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "geo/entity/living_armor.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(LivingArmorEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "textures/entity/living_armor.png");
    }

    @Override
    public ResourceLocation getAnimationResource(LivingArmorEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "animations/entity/living_armor.animation.json");
    }

    @Override
    public void setCustomAnimations(LivingArmorEntity animatable, long instanceId, AnimationState<LivingArmorEntity> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);

        CoreGeoBone head = this.getAnimationProcessor().getBone("head");
        if (head != null) {
            EntityModelData entityData = animationState.getData(DataTickets.ENTITY_MODEL_DATA);
            head.setRotX(entityData.headPitch() * Mth.DEG_TO_RAD);
            head.setRotY(entityData.netHeadYaw() * Mth.DEG_TO_RAD);
        }
    }
}