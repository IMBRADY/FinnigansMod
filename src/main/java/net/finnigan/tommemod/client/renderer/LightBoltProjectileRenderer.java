package net.finnigan.tommemod.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.finnigan.tommemod.client.model.LumapierRodModel;
import net.finnigan.tommemod.client.sound.LumapierRodSoundInstance;
import net.finnigan.tommemod.entity.custom.LumapierHelpers.LightBoltProjectileEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Untextured, fully white crossed quads make the charged projectile read as a huge rod of light.
 */
public class LightBoltProjectileRenderer extends GeoEntityRenderer<LightBoltProjectileEntity> {

    public LightBoltProjectileRenderer(EntityRendererProvider.Context context) {
        super(context, new LumapierRodModel());
    }

    @Override
    protected int getBlockLightLevel(LightBoltProjectileEntity entity, BlockPos pos) {
        return 15;
    }

    @Override
    public void render(LightBoltProjectileEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
                        MultiBufferSource buffer, int packedLight) {
        LumapierRodSoundInstance.ensurePlaying(entity);
        super.render(entity, getRenderYaw(entity, entityYaw, partialTicks), partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    public void preRender(PoseStack poseStack, LightBoltProjectileEntity entity, BakedGeoModel model,
                          MultiBufferSource bufferSource, com.mojang.blaze3d.vertex.VertexConsumer buffer,
                          boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                          float red, float green, float blue, float alpha) {
        // Euler yaw/pitch keeps a fixed roll. Once launched, derive those angles from velocity so
        // the model follows its real path even before a client receives the entity rotation update.
        float yaw = getRenderYaw(entity, entity.getYRot(), partialTick);
        float pitch = getRenderPitch(entity, partialTick);
        if (entity.isLaunched()) {
            Vec3 velocity = entity.getDeltaMovement();
            if (velocity.lengthSqr() > 1.0E-6D) {
                Vec3 direction = velocity.normalize();
                yaw = (float) (Mth.atan2(-direction.x, direction.z) * Mth.RAD_TO_DEG);
                pitch = (float) (-Math.asin(direction.y) * Mth.RAD_TO_DEG);
            }
        }
        poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
        super.preRender(poseStack, entity, model, bufferSource, buffer, isReRender, partialTick,
                packedLight, packedOverlay, red, green, blue, alpha);
    }

    private static float getRenderYaw(LightBoltProjectileEntity entity, float fallbackYaw, float partialTick) {
        if (!entity.isLaunched()) {
            LivingEntity owner = entity.getLumapierOwner();
            if (owner != null) return Mth.rotLerp(partialTick, owner.yRotO, owner.getYRot());
        }
        return entity.isStuck() ? entity.getImpactYaw() : fallbackYaw;
    }

    private static float getRenderPitch(LightBoltProjectileEntity entity, float partialTick) {
        if (!entity.isLaunched()) {
            LivingEntity owner = entity.getLumapierOwner();
            if (owner != null) return Mth.lerp(partialTick, owner.xRotO, owner.getXRot());
        }
        return entity.isStuck() ? entity.getImpactPitch() : entity.getXRot();
    }
}
