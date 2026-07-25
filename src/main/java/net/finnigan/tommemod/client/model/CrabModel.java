package net.finnigan.tommemod.client.model;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.entity.custom.CrabEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class CrabModel extends GeoModel<CrabEntity> {
    @Override
    public ResourceLocation getModelResource(CrabEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "geo/entity/crab.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(CrabEntity animatable) {
        String texture = animatable.getVariant() == CrabEntity.Variant.RED ? "crab_red" : "crab_blue";
        return new ResourceLocation(TommeMod.MOD_ID, "textures/entity/" + texture + ".png");
    }

    @Override
    public ResourceLocation getAnimationResource(CrabEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "animations/entity/crab.animation.json");
    }
}
