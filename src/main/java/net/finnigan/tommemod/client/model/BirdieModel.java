package net.finnigan.tommemod.client.model;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.entity.custom.BirdieEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class BirdieModel extends GeoModel<BirdieEntity> {
    @Override
    public ResourceLocation getModelResource(BirdieEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "geo/entity/birdie.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(BirdieEntity animatable) {
        String texture = switch (animatable.getVariant()) {
            case BLUE -> "bluebird";
            case BROWN -> "brownbird";
            case RED -> "redbird";
        };
        return new ResourceLocation(TommeMod.MOD_ID, "textures/entity/" + texture + ".png");
    }

    @Override
    public ResourceLocation getAnimationResource(BirdieEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "animations/entity/birdie.animation.json");
    }
}