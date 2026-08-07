package net.finnigan.tommemod.client.model;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.entity.custom.SandSharkEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class SandSharkModel extends GeoModel<SandSharkEntity> {
    @Override
    public ResourceLocation getModelResource(SandSharkEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "geo/entity/sand_shark.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(SandSharkEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "textures/entity/sand_shark.png");
    }

    @Override
    public ResourceLocation getAnimationResource(SandSharkEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "animations/entity/sand_shark.animation.json");
    }
}
