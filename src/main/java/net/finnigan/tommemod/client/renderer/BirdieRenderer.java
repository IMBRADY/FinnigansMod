package net.finnigan.tommemod.client.renderer;

import net.finnigan.tommemod.client.model.BirdieModel;
import net.finnigan.tommemod.entity.custom.BirdieEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class BirdieRenderer extends GeoEntityRenderer<BirdieEntity> {
    public BirdieRenderer(EntityRendererProvider.Context context) {
        super(context, new BirdieModel());
        this.shadowRadius = 0.2f;
    }
}