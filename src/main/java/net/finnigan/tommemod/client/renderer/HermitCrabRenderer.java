package net.finnigan.tommemod.client.renderer;

import net.finnigan.tommemod.client.model.HermitCrabModel;
import net.finnigan.tommemod.entity.custom.HermitCrabEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class HermitCrabRenderer extends GeoEntityRenderer<HermitCrabEntity> {
    public HermitCrabRenderer(EntityRendererProvider.Context context) {
        super(context, new HermitCrabModel());
        this.shadowRadius = 0.3f;
    }
}
