package net.finnigan.tommemod.client.renderer;

import net.finnigan.tommemod.client.model.IxeBoxModel;
import net.finnigan.tommemod.entity.custom.IxeHelpers.IxeBoxEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class IxeBoxRenderer extends GeoEntityRenderer<IxeBoxEntity> {
    public IxeBoxRenderer(EntityRendererProvider.Context context) {
        super(context, new IxeBoxModel());
        this.shadowRadius = 0f;
    }
}
