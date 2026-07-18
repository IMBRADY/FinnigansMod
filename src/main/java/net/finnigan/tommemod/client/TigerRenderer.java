package net.finnigan.tommemod.client;

import net.finnigan.tommemod.client.model.TigerModel;
import net.finnigan.tommemod.entity.custom.TigerEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class TigerRenderer extends GeoEntityRenderer<TigerEntity> {
    public TigerRenderer(EntityRendererProvider.Context context) {
        super(context, new TigerModel());
        this.shadowRadius = 0.6f;
    }
}