package net.finnigan.tommemod.client.renderer;

import net.finnigan.tommemod.client.model.RolliePollieModel;
import net.finnigan.tommemod.entity.custom.RolliePollieEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class RolliePollieRenderer extends GeoEntityRenderer<RolliePollieEntity> {
    public RolliePollieRenderer(EntityRendererProvider.Context context) {
        super(context, new RolliePollieModel());
        this.shadowRadius = 0.7f;
    }
}
