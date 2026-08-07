package net.finnigan.tommemod.client.model;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.entity.custom.MiniflyEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class MiniflyModel extends GeoModel<MiniflyEntity> {
    @Override
    public ResourceLocation getModelResource(MiniflyEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "geo/entity/minifly.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(MiniflyEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "textures/entity/minifly.png");
    }

    @Override
    public ResourceLocation getAnimationResource(MiniflyEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "animations/entity/minifly.animation.json");
    }
}
