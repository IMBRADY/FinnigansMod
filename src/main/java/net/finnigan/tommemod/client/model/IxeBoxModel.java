package net.finnigan.tommemod.client.model;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.entity.custom.IxeHelpers.IxeBoxEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class IxeBoxModel extends GeoModel<IxeBoxEntity> {
    @Override
    public ResourceLocation getModelResource(IxeBoxEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "geo/entity/ixe_box.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(IxeBoxEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "textures/entity/ixe_box.png");
    }

    @Override
    public ResourceLocation getAnimationResource(IxeBoxEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "animations/entity/ixe_box.animation.json");
    }
}
