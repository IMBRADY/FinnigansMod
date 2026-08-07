package net.finnigan.tommemod.client.renderer;

import net.finnigan.tommemod.client.model.MiniflyModel;
import net.finnigan.tommemod.entity.custom.MiniflyEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class MiniflyRenderer extends GeoEntityRenderer<MiniflyEntity> {
    public MiniflyRenderer(EntityRendererProvider.Context context) {
        super(context, new MiniflyModel());
        this.shadowRadius = 0.2f;
    }
}
