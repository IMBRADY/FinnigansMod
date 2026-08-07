package net.finnigan.tommemod.client.renderer;

import net.finnigan.tommemod.client.model.WyvernModel;
import net.finnigan.tommemod.entity.custom.WyvernEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class WyvernRenderer extends GeoEntityRenderer<WyvernEntity> {
    public WyvernRenderer(EntityRendererProvider.Context context) {
        super(context, new WyvernModel());
        this.shadowRadius = 0.7f;
    }
}
