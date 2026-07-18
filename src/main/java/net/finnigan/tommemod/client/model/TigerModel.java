package net.finnigan.tommemod.client.model;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.entity.custom.TigerEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

public class TigerModel extends GeoModel<TigerEntity> {
    @Override
    public ResourceLocation getModelResource(TigerEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "geo/entity/tiger.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(TigerEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "textures/entity/tiger.png");
    }

    @Override
    public ResourceLocation getAnimationResource(TigerEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "animations/entity/tiger.animation.json");
    }
    // Tiger dynamic head movement broken (potentially due to BB model head folder
}