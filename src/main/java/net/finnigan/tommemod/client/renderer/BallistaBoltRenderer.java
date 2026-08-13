package net.finnigan.tommemod.client.renderer;

import net.finnigan.tommemod.entity.custom.BallistaHelpers.BallistaBoltEntity;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/** Drawn as an ordinary arrow - the bolt differs from one in behaviour, not appearance. */
public class BallistaBoltRenderer extends ArrowRenderer<BallistaBoltEntity> {

    private static final ResourceLocation TEXTURE = new ResourceLocation("textures/entity/projectiles/arrow.png");

    public BallistaBoltRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(BallistaBoltEntity entity) {
        return TEXTURE;
    }
}
