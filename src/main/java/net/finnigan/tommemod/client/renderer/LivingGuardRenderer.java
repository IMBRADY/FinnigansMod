package net.finnigan.tommemod.client.renderer;

import net.finnigan.tommemod.client.model.LivingGuardModel;
import net.finnigan.tommemod.entity.custom.LivingGuardEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class LivingGuardRenderer extends GeoEntityRenderer<LivingGuardEntity> {
    public LivingGuardRenderer(EntityRendererProvider.Context context) {
        super(context, new LivingGuardModel());
        this.shadowRadius = 0.7f;
    }
}
