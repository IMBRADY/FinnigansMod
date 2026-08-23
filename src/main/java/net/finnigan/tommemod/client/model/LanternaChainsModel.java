package net.finnigan.tommemod.client.model;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.entity.custom.LanternaHelpers.LanternaChainsEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class LanternaChainsModel extends GeoModel<LanternaChainsEntity> {
    @Override
    public ResourceLocation getModelResource(LanternaChainsEntity entity) {
        return new ResourceLocation(TommeMod.MOD_ID, "geo/entity/lanterna_chains.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(LanternaChainsEntity entity) {
        return new ResourceLocation(TommeMod.MOD_ID, "textures/entity/lanterna_chains_attack.png");
    }

    @Override
    public ResourceLocation getAnimationResource(LanternaChainsEntity entity) {
        return new ResourceLocation(TommeMod.MOD_ID, "animations/entity/lanterna_chains.animation.json");
    }
}
