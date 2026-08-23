package net.finnigan.tommemod.client;

import net.finnigan.tommemod.entity.custom.EndDrayk.DraykPose;
import net.finnigan.tommemod.entity.custom.EndDrayk.DraykSegment;
import net.finnigan.tommemod.entity.custom.EndDrayk.EndDraykEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

/**
 * Drives the drayk's ten segment bones from the trail. No keyframes are involved for the body - the only
 * animated bones are the four wing bones under {@code arms}, and this never touches those.
 */
public class EndDraykModel extends GeoModel<EndDraykEntity> {

    /** Model pixels per block. */
    private static final double PIXELS_PER_BLOCK = 16.0D;

    /**
     * Sign of the per-segment pitch rotation. Unlike the position and yaw maths below, this one is not
     * derived - GeckoLib applies no pitch to the pose stack for us to cancel against, so which way a
     * positive xRot tips a bone is a convention. Flip this if the segments pitch the wrong way when the
     * drayk climbs or dives.
     */
    private static final float PITCH_SIGN = 1.0F;

    @Override
    public ResourceLocation getModelResource(EndDraykEntity animatable) {
        return new ResourceLocation("tommemod", "geo/entity/end_drayk.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(EndDraykEntity animatable) {
        return new ResourceLocation("tommemod", "textures/entity/end_drayk.png");
    }

    @Override
    public ResourceLocation getAnimationResource(EndDraykEntity animatable) {
        return new ResourceLocation("tommemod", "animations/entity/end_drayk.animation.json");
    }

    /**
     * Runs after GeckoLib has applied keyframes, so these writes win on the bones they touch.
     *
     * <p>The world-to-bone transform, worked out from the GeckoLib sources rather than guessed:
     * {@code GeoEntityRenderer#applyRotations} has already turned the pose stack by
     * {@code Ry(180 - bodyYaw)}, so a world offset {@code d} becomes model-space
     * {@code m = Ry(bodyYaw - 180) * d} - that is {@code d} yawed by the body rotation, with X and Z
     * then negated. {@code RenderUtils#translateMatrixToBone} applies the bone position as
     * {@code (-posX, posY, posZ)}, and its X negation cancels the one above, which is why only Z ends up
     * flipped here. Bone rotation and scale both happen about the pivot, and the merged geo pivots every
     * segment bone on its own cube centre, so subtracting the pivot lands that centre on the trail point.
     */
    @Override
    public void setCustomAnimations(EndDraykEntity drayk, long instanceId,
                                    AnimationState<EndDraykEntity> state) {
        super.setCustomAnimations(drayk, instanceId, state);

        float partialTick = state.getPartialTick();

        // Match the origin and yaw the renderer itself is using, not the raw tick values: applyRotations
        // rotates by the lerped yBodyRot, and the entity draws at its lerped position.
        Vec3 origin = new Vec3(
                Mth.lerp(partialTick, drayk.xOld, drayk.getX()),
                Mth.lerp(partialTick, drayk.yOld, drayk.getY()),
                Mth.lerp(partialTick, drayk.zOld, drayk.getZ()));
        float bodyYaw = Mth.rotLerp(partialTick, drayk.yBodyRotO, drayk.yBodyRot);

        for (DraykSegment seg : DraykSegment.values()) {
            CoreGeoBone bone = this.getAnimationProcessor().getBone(seg.boneName());
            if (bone == null) continue;

            DraykPose pose = drayk.poseOf(seg);
            Vec3 local = pose.lerpPos(partialTick).subtract(origin).yRot(bodyYaw * Mth.DEG_TO_RAD);

            bone.setPosX((float) ( local.x * PIXELS_PER_BLOCK) + bone.getPivotX());
            bone.setPosY((float) ( local.y * PIXELS_PER_BLOCK) - bone.getPivotY());
            bone.setPosZ((float) (-local.z * PIXELS_PER_BLOCK) - bone.getPivotZ());

            bone.setRotY(Mth.wrapDegrees(bodyYaw - pose.lerpYaw(partialTick)) * Mth.DEG_TO_RAD);
            bone.setRotX(PITCH_SIGN * pose.lerpPitch(partialTick) * Mth.DEG_TO_RAD);

            bone.setScaleX(seg.scale());
            bone.setScaleY(seg.scale());
            bone.setScaleZ(seg.scale());
        }
    }
}
