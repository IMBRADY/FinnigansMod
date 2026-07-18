package net.finnigan.tommemod.client;

import net.finnigan.tommemod.client.model.MantaModel;
import net.finnigan.tommemod.entity.custom.MantaEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class MantaRenderer extends GeoEntityRenderer<MantaEntity> {
    public MantaRenderer(EntityRendererProvider.Context context) {
        super(context, new MantaModel());
        this.shadowRadius = 0.4f;
    }
}