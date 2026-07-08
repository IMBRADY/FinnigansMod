package net.finnigan.tommemod.client;

import net.finnigan.tommemod.entity.custom.JellyfishEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class JellyfishRenderer extends GeoEntityRenderer<JellyfishEntity> {
    public JellyfishRenderer(EntityRendererProvider.Context context) {
        super(context, new JellyfishModel());
        this.shadowRadius = 0.3f;
    }
}