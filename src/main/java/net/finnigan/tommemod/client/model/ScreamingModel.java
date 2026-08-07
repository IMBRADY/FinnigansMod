package net.finnigan.tommemod.client.model;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.entity.custom.ScreamingEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class ScreamingModel extends GeoModel<ScreamingEntity> {
    @Override
    public ResourceLocation getModelResource(ScreamingEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "geo/entity/screaming.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(ScreamingEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "textures/entity/screaming.png");
    }

    @Override
    public ResourceLocation getAnimationResource(ScreamingEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "animations/entity/screaming.animation.json");
    }
}
