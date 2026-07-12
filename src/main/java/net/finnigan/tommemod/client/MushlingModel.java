package net.finnigan.tommemod.client;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.entity.custom.MushlingEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class MushlingModel extends GeoModel<MushlingEntity> {

    private static final ResourceLocation TEXTURE_ONE =
            new ResourceLocation(TommeMod.MOD_ID, "textures/entity/mushling/mushling_1.png");
    private static final ResourceLocation TEXTURE_TWO =
            new ResourceLocation(TommeMod.MOD_ID, "textures/entity/mushling/mushling_2.png");

    @Override
    public ResourceLocation getModelResource(MushlingEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "geo/entity/mushling.geo.json"); // whatever you already have here
    }

    @Override
    public ResourceLocation getTextureResource(MushlingEntity animatable) {
        return switch (animatable.getVariant()) {
            case 1 -> TEXTURE_TWO;
            default -> TEXTURE_ONE;
        };
    }

    @Override
    public ResourceLocation getAnimationResource(MushlingEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "animations/entity/mushling.animation.json"); // whatever you already have here
    }
}