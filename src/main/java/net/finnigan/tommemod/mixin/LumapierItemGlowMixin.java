package net.finnigan.tommemod.mixin;

import net.finnigan.tommemod.item.custom.LumapierItem;
import net.finnigan.tommemod.item.custom.LanternaItem;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Renders self-lit unique weapons at maximum item-model light. */
@Mixin(ItemRenderer.class)
public abstract class LumapierItemGlowMixin {
    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int tommemod$renderLumapierFullBright(int packedLight, ItemStack stack) {
        if (stack.getItem() instanceof LumapierItem) return 0xF000F0;
        if (stack.getItem() instanceof LanternaItem
                && (!stack.hasTag() || !stack.getTag().getBoolean("LanternaFired"))) {
            return 0xF000F0;
        }
        return packedLight;
    }
}
