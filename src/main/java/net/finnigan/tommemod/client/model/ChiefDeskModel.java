package net.finnigan.tommemod.client.model;

import net.finnigan.tommemod.TommeMod;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.model.GeoModel;

/**
 * Shared by the placed Chief Desk and its item form, hence the loose type parameter - both point at
 * the same geometry, texture and (currently empty) animation file.
 */
public class ChiefDeskModel<T extends GeoAnimatable> extends GeoModel<T> {

    private static final ResourceLocation MODEL =
            new ResourceLocation(TommeMod.MOD_ID, "geo/block/chief_desk.geo.json");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(TommeMod.MOD_ID, "textures/block/chiefdesk.png");
    private static final ResourceLocation ANIMATION =
            new ResourceLocation(TommeMod.MOD_ID, "animations/block/chief_desk.animation.json");

    @Override
    public ResourceLocation getModelResource(T animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(T animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(T animatable) {
        return ANIMATION;
    }
}
