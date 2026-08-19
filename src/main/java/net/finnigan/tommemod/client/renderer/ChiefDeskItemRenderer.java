package net.finnigan.tommemod.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.finnigan.tommemod.client.model.ChiefDeskModel;
import net.finnigan.tommemod.item.custom.ChiefDeskBlockItem;
import net.minecraft.client.renderer.MultiBufferSource;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/**
 * Draws the Chief Desk as an item.
 *
 * <p>GeckoLib places a model's own origin at (0.5, 0.51, 0.5), which is right for a mesh that fills
 * one block from the ground up and wrong for this one: the desk is two blocks wide, its slanted top
 * reaches 1.55 blocks, and the pages hang back to z=-1.15. Left alone, the icon sat well above the
 * middle of its inventory slot and ran off the top of it - the "floating" this class exists to fix.
 *
 * <p>{@link #preRender} therefore re-centres the mesh's bounding box on the origin, which is the
 * arrangement every {@code display} block in models/item/chief_desk.json is written against, and the
 * same one vanilla's own block items are rendered in.
 */
public class ChiefDeskItemRenderer extends GeoItemRenderer<ChiefDeskBlockItem> {

    /**
     * Centre of the mesh's bounding box, in blocks, measured off geo/block/chief_desk.geo.json with
     * every cube rotation applied: x -1..1, y 0..1.556, z -1.155..1.055. Re-measure if the geometry
     * changes shape.
     */
    private static final float CENTRE_X = 0.0F;
    private static final float CENTRE_Y = 0.778F;
    private static final float CENTRE_Z = -0.05F;

    /** What GeckoLib's own preRender offsets the model by, and what has to come back off. */
    private static final float GECKOLIB_OFFSET_X = 0.5F;
    private static final float GECKOLIB_OFFSET_Y = 0.51F;
    private static final float GECKOLIB_OFFSET_Z = 0.5F;

    public ChiefDeskItemRenderer() {
        super(new ChiefDeskModel<>());
    }

    @Override
    public void preRender(PoseStack poseStack, ChiefDeskBlockItem animatable, BakedGeoModel model,
                          MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                          float partialTick, int packedLight, int packedOverlay,
                          float red, float green, float blue, float alpha) {
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick,
                packedLight, packedOverlay, red, green, blue, alpha);

        // Guarded on isReRender for the same reason GeckoLib guards its own offset: a re-render is
        // already inside the transform this set up, so applying it twice would double the shift.
        if (!isReRender) {
            poseStack.translate(-GECKOLIB_OFFSET_X - CENTRE_X,
                    -GECKOLIB_OFFSET_Y - CENTRE_Y,
                    -GECKOLIB_OFFSET_Z - CENTRE_Z);
        }
    }
}
