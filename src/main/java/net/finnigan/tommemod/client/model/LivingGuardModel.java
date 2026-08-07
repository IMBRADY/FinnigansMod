package net.finnigan.tommemod.client.model;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.entity.custom.LivingGuardEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class LivingGuardModel extends GeoModel<LivingGuardEntity> {
    @Override
    public ResourceLocation getModelResource(LivingGuardEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "geo/entity/living_guard.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(LivingGuardEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "textures/entity/living_guard.png");
    }

    @Override
    public ResourceLocation getAnimationResource(LivingGuardEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "animations/entity/living_guard.animation.json");
    }
}
