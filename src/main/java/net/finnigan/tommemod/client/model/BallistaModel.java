package net.finnigan.tommemod.client.model;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.entity.custom.BallistaEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

/**
 * Aims the crossbow arm at whatever the Ballista is shooting.
 *
 * The arm is bone8 - the whole head of the crossbow - and it is the only part that moves; the frame
 * under it is rendered at the rotation it was placed with and stays there. Both angles arrive as the
 * entity's head rotation, which vanilla already syncs and GeckoLib already interpolates per frame, so
 * the swing is smooth without anything here having to track it.
 */
public class BallistaModel extends GeoModel<BallistaEntity> {

    /** The crossbow head. Named by Blockbench, and referenced as-is rather than renamed, so that
     * re-exporting the model from Blockbench doesn't quietly break the aiming. */
    private static final String ARM_BONE = "bone8";

    @Override
    public ResourceLocation getModelResource(BallistaEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "geo/entity/ballista.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(BallistaEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "textures/entity/ballista/ballista.png");
    }

    @Override
    public ResourceLocation getAnimationResource(BallistaEntity animatable) {
        return new ResourceLocation(TommeMod.MOD_ID, "animations/entity/ballista.animation.json");
    }

    @Override
    public void setCustomAnimations(BallistaEntity animatable, long instanceId,
                                    AnimationState<BallistaEntity> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);

        CoreGeoBone arm = this.getAnimationProcessor().getBone(ARM_BONE);
        if (arm == null) return;

        EntityModelData entityData = animationState.getData(DataTickets.ENTITY_MODEL_DATA);
        arm.setRotX(entityData.headPitch() * Mth.DEG_TO_RAD);
        arm.setRotY(entityData.netHeadYaw() * Mth.DEG_TO_RAD);
    }
}
