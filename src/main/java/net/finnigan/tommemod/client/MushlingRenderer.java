package net.finnigan.tommemod.client; // match wherever MushlingModel actually lives

import net.finnigan.tommemod.client.model.MushlingModel;
import net.finnigan.tommemod.entity.custom.MushlingEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class MushlingRenderer extends GeoEntityRenderer<MushlingEntity> {

    public MushlingRenderer(EntityRendererProvider.Context context) {
        super(context, new MushlingModel());
    }
}