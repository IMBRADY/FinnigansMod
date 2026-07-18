package net.finnigan.tommemod.client.model;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.entity.custom.MantaEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class MantaModel extends GeoModel<MantaEntity> {
    @Override
    public ResourceLocation getModelResource(MantaEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "geo/entity/manta.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(MantaEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "textures/entity/manta.png");
    }

    @Override
    public ResourceLocation getAnimationResource(MantaEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "animations/entity/manta.animation.json");
    }
}