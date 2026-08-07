package net.finnigan.tommemod.client.model;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.entity.custom.WyvernEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class WyvernModel extends GeoModel<WyvernEntity> {
    @Override
    public ResourceLocation getModelResource(WyvernEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "geo/entity/wyvern.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(WyvernEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "textures/entity/wyvern.png");
    }

    @Override
    public ResourceLocation getAnimationResource(WyvernEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "animations/entity/wyvern.animation.json");
    }
}
