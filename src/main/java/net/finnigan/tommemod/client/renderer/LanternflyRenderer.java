package net.finnigan.tommemod.client.renderer;

import net.finnigan.tommemod.client.model.LanternflyModel;
import net.finnigan.tommemod.entity.custom.LanternflyEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class LanternflyRenderer extends GeoEntityRenderer<LanternflyEntity> {
    public LanternflyRenderer(EntityRendererProvider.Context context) {
        super(context, new LanternflyModel());
        this.shadowRadius = 0.5f;
    }
}
