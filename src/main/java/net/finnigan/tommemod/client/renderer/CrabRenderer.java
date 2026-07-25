package net.finnigan.tommemod.client.renderer;

import net.finnigan.tommemod.client.model.CrabModel;
import net.finnigan.tommemod.entity.custom.CrabEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class CrabRenderer extends GeoEntityRenderer<CrabEntity> {
    public CrabRenderer(EntityRendererProvider.Context context) {
        super(context, new CrabModel());
        this.shadowRadius = 0.2f;
    }
}
