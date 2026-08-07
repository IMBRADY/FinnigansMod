package net.finnigan.tommemod.client.renderer;

import net.finnigan.tommemod.client.model.ScreamingModel;
import net.finnigan.tommemod.entity.custom.ScreamingEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class ScreamingRenderer extends GeoEntityRenderer<ScreamingEntity> {
    public ScreamingRenderer(EntityRendererProvider.Context context) {
        super(context, new ScreamingModel());
        this.shadowRadius = 0.4f;
    }
}
