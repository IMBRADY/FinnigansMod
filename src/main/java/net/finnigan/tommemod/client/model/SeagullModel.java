package net.finnigan.tommemod.client.model;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.entity.custom.SeagullEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class SeagullModel extends GeoModel<SeagullEntity> {
    @Override
    public ResourceLocation getModelResource(SeagullEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "geo/entity/seagull.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(SeagullEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "textures/entity/seagull.png");
    }

    @Override
    public ResourceLocation getAnimationResource(SeagullEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "animations/entity/seagull.animation.json");
    }
}