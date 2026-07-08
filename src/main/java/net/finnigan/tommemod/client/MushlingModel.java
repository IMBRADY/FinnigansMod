package net.finnigan.tommemod.client;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.entity.custom.MushlingEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class MushlingModel extends GeoModel<MushlingEntity> {
    @Override
    public ResourceLocation getModelResource(MushlingEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "geo/entity/mushling.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(MushlingEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "textures/entity/mushling.png");
    }

    @Override
    public ResourceLocation getAnimationResource(MushlingEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "animations/entity/mushling.animation.json");
    }
}