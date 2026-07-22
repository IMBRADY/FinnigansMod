package net.finnigan.tommemod.client.model;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.entity.custom.AmethystCutlassHelpers.AmethystBeamEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class AmethystBeamModel extends GeoModel<AmethystBeamEntity> {

    @Override
    public ResourceLocation getModelResource(AmethystBeamEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "geo/entity/amethyst_beam.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(AmethystBeamEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "textures/entity/amethyst_beam.png");
    }

    @Override
    public ResourceLocation getAnimationResource(AmethystBeamEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "animations/entity/amethyst_beam.animation.json");
    }
}