package net.finnigan.tommemod.client.renderer;

import net.finnigan.tommemod.client.model.SeagullModel;
import net.finnigan.tommemod.entity.custom.SeagullEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class SeagullRenderer extends GeoEntityRenderer<SeagullEntity> {
    public SeagullRenderer(EntityRendererProvider.Context context) {
        super(context, new SeagullModel());
        this.shadowRadius = 0.25f;
    }
}