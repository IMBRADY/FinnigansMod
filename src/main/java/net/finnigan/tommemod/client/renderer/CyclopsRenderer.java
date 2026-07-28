package net.finnigan.tommemod.client.renderer;

import net.finnigan.tommemod.client.model.CyclopsModel;
import net.finnigan.tommemod.entity.custom.CyclopsEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class CyclopsRenderer extends GeoEntityRenderer<CyclopsEntity> {
    public CyclopsRenderer(EntityRendererProvider.Context context) {
        super(context, new CyclopsModel());
        this.shadowRadius = 0.7f;
    }
}
