package net.finnigan.tommemod.client;

import net.finnigan.tommemod.client.model.ButterflyModel;
import net.finnigan.tommemod.entity.custom.ButterflyEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class ButterflyRenderer extends GeoEntityRenderer<ButterflyEntity> {
    public ButterflyRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new ButterflyModel());
        this.shadowRadius = 0.2f;
    }
}