package net.finnigan.tommemod.mixin;

import net.finnigan.tommemod.client.light.DynamicLightManager;
import net.minecraft.client.particle.Particle;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets particle vertices sample the same client-side dynamic lights as world geometry. */
@Mixin(Particle.class)
public abstract class DynamicParticleLightMixin {
    @Shadow protected double x;
    @Shadow protected double y;
    @Shadow protected double z;

    @Inject(method = "getLightColor", at = @At("RETURN"), cancellable = true)
    private void tommemod$applyDynamicParticleLight(float partialTick, CallbackInfoReturnable<Integer> cir) {
        int dynamicBlockLight = DynamicLightManager.getParticleLightLevel(new Vec3(x, y, z), partialTick);
        int packedLight = cir.getReturnValue();
        int existingBlockLight = packedLight >> 4 & 15;
        if (dynamicBlockLight > existingBlockLight) {
            cir.setReturnValue((packedLight & ~0xF0) | dynamicBlockLight << 4);
        }
    }
}
