package net.finnigan.tommemod.mixin;

import net.finnigan.tommemod.client.light.DynamicLightManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Lets nearby dynamic sources illuminate any item rendered in the local player's hands. */
@Mixin(ItemRenderer.class)
public abstract class LumapierItemGlowMixin {
    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int tommemod$lightHeldItemFromDynamicSources(int packedLight, ItemStack stack,
                                                         ItemDisplayContext displayContext) {
        if (displayContext != ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                && displayContext != ItemDisplayContext.FIRST_PERSON_RIGHT_HAND) {
            return packedLight;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return packedLight;

        int emittedLight = DynamicLightManager.getParticleLightLevel(
                minecraft.gameRenderer.getMainCamera().getPosition(), minecraft.getFrameTime());
        int existingBlockLight = packedLight >> 4 & 15;
        return emittedLight <= existingBlockLight
                ? packedLight
                : (packedLight & ~0xF0) | emittedLight << 4;
    }
}
