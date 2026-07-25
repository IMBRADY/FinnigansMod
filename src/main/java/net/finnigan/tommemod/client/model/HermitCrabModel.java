package net.finnigan.tommemod.client.model;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.entity.custom.HermitCrabEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class HermitCrabModel extends GeoModel<HermitCrabEntity> {
    @Override
    public ResourceLocation getModelResource(HermitCrabEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "geo/entity/hermit_crab.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(HermitCrabEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "textures/entity/hermit_crab.png");
    }

    @Override
    public ResourceLocation getAnimationResource(HermitCrabEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "animations/entity/hermit_crab.animation.json");
    }
}
