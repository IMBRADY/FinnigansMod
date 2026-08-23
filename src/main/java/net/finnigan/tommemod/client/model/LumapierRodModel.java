package net.finnigan.tommemod.client.model;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.entity.custom.LumapierHelpers.LightBoltProjectileEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/** GeckoLib resources supplied for Lumapier's expanding light rod. */
public class LumapierRodModel extends GeoModel<LightBoltProjectileEntity> {
    @Override
    public ResourceLocation getModelResource(LightBoltProjectileEntity entity) {
        return new ResourceLocation(TommeMod.MOD_ID, "geo/entity/lumapier_rod.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(LightBoltProjectileEntity entity) {
        return new ResourceLocation(TommeMod.MOD_ID, "textures/entity/lumapier_rod.png");
    }

    @Override
    public ResourceLocation getAnimationResource(LightBoltProjectileEntity entity) {
        return new ResourceLocation(TommeMod.MOD_ID, "animations/entity/lumapier_rod.animation.json");
    }
}
