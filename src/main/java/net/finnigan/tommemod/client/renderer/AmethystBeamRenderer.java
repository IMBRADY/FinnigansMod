package net.finnigan.tommemod.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.finnigan.tommemod.client.model.AmethystBeamModel;
import net.finnigan.tommemod.entity.custom.AmethystCutlassHelpers.AmethystBeamEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class AmethystBeamRenderer extends GeoEntityRenderer<AmethystBeamEntity> {

    public AmethystBeamRenderer(EntityRendererProvider.Context context) {
        super(context, new AmethystBeamModel());
    }

    @Override
    public void preRender(PoseStack poseStack, AmethystBeamEntity animatable, BakedGeoModel model,
                          MultiBufferSource bufferSource, com.mojang.blaze3d.vertex.VertexConsumer buffer,
                          boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                          float red, float green, float blue, float alpha) {

        Vec3 look = animatable.getViewVector(partialTick);
        Vector3f dir = new Vector3f((float) look.x, (float) look.y, (float) look.z).normalize();
        Vector3f up = new Vector3f(0.0F, 1.0F, 0.0F); // model's default length axis

        Quaternionf rotation = new Quaternionf().rotationTo(up, dir);
        poseStack.mulPose(rotation);

        float lengthScale = animatable.getLength();
        poseStack.scale(1.0F, lengthScale, 1.0F);

        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick,
                packedLight, packedOverlay, red, green, blue, alpha);
    }
}