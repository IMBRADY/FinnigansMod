package net.finnigan.tommemod.client.renderer;

import net.finnigan.tommemod.client.model.BallistaModel;
import net.finnigan.tommemod.entity.custom.BallistaEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class BallistaRenderer extends GeoEntityRenderer<BallistaEntity> {

    public BallistaRenderer(EntityRendererProvider.Context context) {
        super(context, new BallistaModel());
        this.shadowRadius = 0.6f;
    }
}
