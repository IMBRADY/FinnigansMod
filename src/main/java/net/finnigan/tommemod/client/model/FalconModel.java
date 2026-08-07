package net.finnigan.tommemod.client.model;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.entity.custom.FalconEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class FalconModel extends GeoModel<FalconEntity> {
    @Override
    public ResourceLocation getModelResource(FalconEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "geo/entity/falcon.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(FalconEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "textures/entity/falcon.png");
    }

    @Override
    public ResourceLocation getAnimationResource(FalconEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "animations/entity/falcon.animation.json");
    }
}
