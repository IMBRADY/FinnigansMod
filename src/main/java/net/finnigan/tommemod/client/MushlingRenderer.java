package net.finnigan.tommemod.client;

import net.finnigan.tommemod.client.MushlingModel;
import net.finnigan.tommemod.entity.custom.MushlingEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class MushlingRenderer extends GeoEntityRenderer<MushlingEntity> {
    public MushlingRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new MushlingModel());
    }
}