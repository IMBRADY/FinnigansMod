package net.finnigan.tommemod.client;

import net.finnigan.tommemod.client.model.EndLanternModel;
import net.finnigan.tommemod.entity.custom.EndLanternEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class EndLanternRenderer extends GeoEntityRenderer<EndLanternEntity> {
    public EndLanternRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new EndLanternModel());
        this.shadowRadius = 0.15f;
    }
}