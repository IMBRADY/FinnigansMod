package net.finnigan.tommemod.client;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.entity.custom.EndLanternEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class EndLanternModel extends GeoModel<EndLanternEntity> {
    @Override
    public ResourceLocation getModelResource(EndLanternEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "geo/entity/end_lantern.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(EndLanternEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "textures/entity/end_lantern.png");
    }

    @Override
    public ResourceLocation getAnimationResource(EndLanternEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "animations/entity/end_lantern.animation.json");
    }
}