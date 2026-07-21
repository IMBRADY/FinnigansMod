package net.finnigan.tommemod.client.renderer;

import net.finnigan.tommemod.client.model.DungeonCrabModel;
import net.finnigan.tommemod.client.model.LivingArmorModel;
import net.finnigan.tommemod.entity.custom.LivingArmorEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class LivingArmorRenderer extends GeoEntityRenderer<LivingArmorEntity> {
    public LivingArmorRenderer(EntityRendererProvider.Context context) {
        super(context, new LivingArmorModel());
        this.shadowRadius = 0.5f;
    }
}