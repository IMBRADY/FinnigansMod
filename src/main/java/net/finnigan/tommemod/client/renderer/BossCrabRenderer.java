package net.finnigan.tommemod.client.renderer;

import net.finnigan.tommemod.client.BossCrabModel;
import net.finnigan.tommemod.entity.custom.Bosses.BossCrab.BossCrabEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class BossCrabRenderer extends GeoEntityRenderer<BossCrabEntity> {
    public BossCrabRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new BossCrabModel());
        this.shadowRadius = 0.9f;
    }
}