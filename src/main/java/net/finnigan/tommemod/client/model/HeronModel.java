package net.finnigan.tommemod.client.model;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.entity.custom.HeronEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class HeronModel extends GeoModel<HeronEntity> {
    @Override
    public ResourceLocation getModelResource(HeronEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "geo/entity/heron.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(HeronEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "textures/entity/heron.png");
    }

    @Override
    public ResourceLocation getAnimationResource(HeronEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "animations/entity/heron.animation.json");
    }
}
