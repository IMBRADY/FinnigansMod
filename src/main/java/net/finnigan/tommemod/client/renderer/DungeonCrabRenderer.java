package net.finnigan.tommemod.client.renderer;

import net.finnigan.tommemod.client.model.DungeonCrabModel;
import net.finnigan.tommemod.entity.custom.DungeonCrabEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class DungeonCrabRenderer extends GeoEntityRenderer<DungeonCrabEntity> {
    public DungeonCrabRenderer(EntityRendererProvider.Context context) {
        super(context, new DungeonCrabModel());
        this.shadowRadius = 0.5f;
    }
}