package net.finnigan.tommemod.client.model;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.entity.custom.DungeonCrabEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class DungeonCrabModel extends GeoModel<DungeonCrabEntity> {
    @Override
    public ResourceLocation getModelResource(DungeonCrabEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "geo/entity/dungeon_crab.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(DungeonCrabEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "textures/entity/dungeon_crab.png");
    }

    @Override
    public ResourceLocation getAnimationResource(DungeonCrabEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "animations/entity/dungeon_crab.animation.json");
    }
}