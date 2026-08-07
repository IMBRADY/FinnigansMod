package net.finnigan.tommemod.client.renderer;

import net.finnigan.tommemod.client.model.PurlingModel;
import net.finnigan.tommemod.entity.custom.PurlingEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class PurlingRenderer extends GeoEntityRenderer<PurlingEntity> {
    public PurlingRenderer(EntityRendererProvider.Context context) {
        super(context, new PurlingModel());
        this.shadowRadius = 0.6f;
    }
}
