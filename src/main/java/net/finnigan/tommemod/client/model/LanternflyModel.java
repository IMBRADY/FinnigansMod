package net.finnigan.tommemod.client.model;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.entity.custom.LanternflyEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class LanternflyModel extends GeoModel<LanternflyEntity> {
    @Override
    public ResourceLocation getModelResource(LanternflyEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "geo/entity/lanternfly.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(LanternflyEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "textures/entity/lanternfly.png");
    }

    @Override
    public ResourceLocation getAnimationResource(LanternflyEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "animations/entity/lanternfly.animation.json");
    }
}
