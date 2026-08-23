package net.finnigan.tommemod.mixin;

import net.finnigan.tommemod.client.light.DynamicLightManager;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies registered source light while client chunk geometry is built. */
@Mixin(LevelRenderer.class)
public abstract class DynamicLightLevelMixin {
    @Inject(method = "getLightColor(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)I",
            at = @At("RETURN"), cancellable = true)
    private static void tommemod$applyDynamicLights(BlockAndTintGetter level, BlockState state, BlockPos pos,
                                                     CallbackInfoReturnable<Integer> cir) {
        int dynamicBlockLight = DynamicLightManager.getLightLevel(pos);
        int packedLight = cir.getReturnValue();
        int existingBlockLight = packedLight >> 4 & 15;
        if (dynamicBlockLight > existingBlockLight) cir.setReturnValue((packedLight & ~0xF0) | dynamicBlockLight << 4);
    }
}
