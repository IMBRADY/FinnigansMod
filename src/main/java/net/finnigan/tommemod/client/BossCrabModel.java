package net.finnigan.tommemod.client;

import net.finnigan.tommemod.entity.custom.Bosses.BossCrab.BossCrabEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class BossCrabModel extends GeoModel<BossCrabEntity> {

    @Override
    public ResourceLocation getModelResource(BossCrabEntity animatable) {
        return new ResourceLocation("tommemod", "geo/entity/boss_crab.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(BossCrabEntity animatable) {
        return new ResourceLocation("tommemod", "textures/entity/boss_crab.png");
    }

    @Override
    public ResourceLocation getAnimationResource(BossCrabEntity animatable) {
        return new ResourceLocation("tommemod", "animations/entity/boss_crab.animation.json");
    }
}