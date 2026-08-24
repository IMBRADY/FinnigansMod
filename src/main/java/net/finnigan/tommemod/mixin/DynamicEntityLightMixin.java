package net.finnigan.tommemod.mixin;

import net.finnigan.tommemod.client.light.DynamicLightManager;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies registered dynamic block light to the packed light used by all entity renderers. */
@Mixin(EntityRenderer.class)
public abstract class DynamicEntityLightMixin<T extends Entity> {
    @Inject(method = "getPackedLightCoords", at = @At("RETURN"), cancellable = true)
    private void tommemod$applyDynamicEntityLight(T entity, float partialTick, CallbackInfoReturnable<Integer> cir) {
        int dynamicBlockLight = DynamicLightManager.getRenderedLightLevel(
                entity.getPosition(partialTick).add(0.0D, entity.getBbHeight() * 0.5D, 0.0D), partialTick);
        int packedLight = cir.getReturnValue();
        int existingBlockLight = packedLight >> 4 & 15;
        if (dynamicBlockLight > existingBlockLight) cir.setReturnValue((packedLight & ~0xF0) | dynamicBlockLight << 4);
    }
}
