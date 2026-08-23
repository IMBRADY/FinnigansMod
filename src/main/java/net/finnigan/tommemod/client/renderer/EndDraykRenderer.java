package net.finnigan.tommemod.client.renderer;

import net.finnigan.tommemod.client.EndDraykModel;
import net.finnigan.tommemod.entity.custom.EndDrayk.EndDraykEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * All ten segments render from this one renderer - the parts have no renderers of their own. The bone
 * placement that spreads them along the trail lives in {@link EndDraykModel}.
 */
public class EndDraykRenderer extends GeoEntityRenderer<EndDraykEntity> {
    public EndDraykRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new EndDraykModel());
        this.shadowRadius = 0.0F;   // segments are spread out; one blob shadow looks wrong
    }
}
