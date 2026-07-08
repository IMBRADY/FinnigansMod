package net.finnigan.tommemod.client;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.entity.custom.ButterflyEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class ButterflyModel extends GeoModel<ButterflyEntity> {
    @Override
    public ResourceLocation getModelResource(ButterflyEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "geo/entity/butterfly.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(ButterflyEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "textures/entity/butterfly.png");
    }

    @Override
    public ResourceLocation getAnimationResource(ButterflyEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "animations/entity/butterfly.animation.json");
    }
}