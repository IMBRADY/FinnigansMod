package net.finnigan.tommemod.client.renderer;

import net.finnigan.tommemod.client.model.FalconModel;
import net.finnigan.tommemod.entity.custom.FalconEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class FalconRenderer extends GeoEntityRenderer<FalconEntity> {
    public FalconRenderer(EntityRendererProvider.Context context) {
        super(context, new FalconModel());
        this.shadowRadius = 0.3f;
    }
}
