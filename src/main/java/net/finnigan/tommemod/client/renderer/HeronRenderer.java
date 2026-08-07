package net.finnigan.tommemod.client.renderer;

import net.finnigan.tommemod.client.model.HeronModel;
import net.finnigan.tommemod.entity.custom.HeronEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class HeronRenderer extends GeoEntityRenderer<HeronEntity> {
    public HeronRenderer(EntityRendererProvider.Context context) {
        super(context, new HeronModel());
        this.shadowRadius = 0.35f;
    }
}
