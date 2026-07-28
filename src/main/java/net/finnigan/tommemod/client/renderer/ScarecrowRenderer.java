package net.finnigan.tommemod.client.renderer;

import net.finnigan.tommemod.client.model.ScarecrowModel;
import net.finnigan.tommemod.entity.custom.ScarecrowEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class ScarecrowRenderer extends GeoEntityRenderer<ScarecrowEntity> {
    public ScarecrowRenderer(EntityRendererProvider.Context context) {
        super(context, new ScarecrowModel());
        this.shadowRadius = 0.6f;
    }
}
