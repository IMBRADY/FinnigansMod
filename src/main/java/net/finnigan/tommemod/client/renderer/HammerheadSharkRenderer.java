package net.finnigan.tommemod.client.renderer;

import net.finnigan.tommemod.client.model.HammerheadSharkModel;
import net.finnigan.tommemod.entity.custom.HammerheadSharkEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class HammerheadSharkRenderer extends GeoEntityRenderer<HammerheadSharkEntity> {
    public HammerheadSharkRenderer(EntityRendererProvider.Context context) {
        super(context, new HammerheadSharkModel());
        this.shadowRadius = 0.6f;
    }
}
