package net.finnigan.tommemod.client.model;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.entity.custom.DuckEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class DuckModel extends GeoModel<DuckEntity> {
    @Override
    public ResourceLocation getModelResource(DuckEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "geo/entity/duck.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(DuckEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "textures/entity/duck.png");
    }

    @Override
    public ResourceLocation getAnimationResource(DuckEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "animations/entity/duck.animation.json");
    }
}
