package net.finnigan.tommemod.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * Shared spin orientation for the End Scythe / Ixe crossed-quad projectiles. Both used to spin around
 * the world Y axis; they now spin around the line running from the shooter's head out to the
 * projectile, so the sprite reads as a disc face-on to whoever threw it however it's flying.
 * The crossed quads are modelled around their own local +Y, so this points that local axis along the
 * head-to-projectile line first and then applies the spin about it - keeping the existing look and
 * only changing which way its axis points.
 */
public final class ProjectileSpinAxis {

    private ProjectileSpinAxis() {
    }

    /**
     * @param owner the shooter, or null if the client doesn't have them loaded - in which case this
     *              falls back to the original world-Y spin rather than picking an arbitrary axis.
     */
    public static void apply(PoseStack poseStack, Entity projectile, Entity owner, float spinDegrees, float partialTicks) {
        if (owner != null) {
            Vec3 axis = projectile.getPosition(partialTicks).subtract(owner.getEyePosition(partialTicks));
            if (axis.lengthSqr() > 1.0E-6) {
                axis = axis.normalize();
                poseStack.mulPose(new Quaternionf().rotateTo(
                        0.0F, 1.0F, 0.0F, (float) axis.x, (float) axis.y, (float) axis.z));
            }
        }
        poseStack.mulPose(Axis.YP.rotationDegrees(spinDegrees));
    }
}
