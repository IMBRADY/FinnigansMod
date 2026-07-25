package net.finnigan.tommemod.client.renderer;

import net.finnigan.tommemod.client.model.DuckModel;
import net.finnigan.tommemod.entity.custom.DuckEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class DuckRenderer extends GeoEntityRenderer<DuckEntity> {
    public DuckRenderer(EntityRendererProvider.Context context) {
        super(context, new DuckModel());
        this.shadowRadius = 0.3f;
    }
}
