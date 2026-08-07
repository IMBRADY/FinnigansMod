package net.finnigan.tommemod.client.model;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.entity.custom.PurlingEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class PurlingModel extends GeoModel<PurlingEntity> {
    @Override
    public ResourceLocation getModelResource(PurlingEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "geo/entity/purling.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(PurlingEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "textures/entity/purling.png");
    }

    @Override
    public ResourceLocation getAnimationResource(PurlingEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "animations/entity/purling.animation.json");
    }
}
