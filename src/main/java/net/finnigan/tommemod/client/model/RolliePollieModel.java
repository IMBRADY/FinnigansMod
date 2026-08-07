package net.finnigan.tommemod.client.model;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.entity.custom.RolliePollieEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class RolliePollieModel extends GeoModel<RolliePollieEntity> {
    @Override
    public ResourceLocation getModelResource(RolliePollieEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "geo/entity/rollie_pollie.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(RolliePollieEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "textures/entity/rollie_pollie.png");
    }

    @Override
    public ResourceLocation getAnimationResource(RolliePollieEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "animations/entity/rollie_pollie.animation.json");
    }
}
