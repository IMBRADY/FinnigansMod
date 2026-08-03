package net.finnigan.tommemod.mixin;

import net.finnigan.tommemod.util.ModTags;
import net.finnigan.tommemod.util.UniqueNameStyler;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gives every Unique-tagged item its animated gradient name (see UniqueNameStyler).
 * Hooked at getHoverName rather than at the tooltip event because this is where every display of an
 * item's name goes through - the hover tooltip, the hotbar name popup, item frames, anvils - so all
 * of them animate from one place. Client-only: names shown to the server (death messages, container
 * titles) are deliberately left plain.
 * A renamed stack keeps its custom name untouched; the gradient is for the item's own identity.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackUniqueNameMixin {

    @Inject(method = "getHoverName", at = @At("RETURN"), cancellable = true)
    private void tommemod$gradientUniqueName(CallbackInfoReturnable<Component> cir) {
        ItemStack stack = (ItemStack) (Object) this;
        if (!stack.is(ModTags.Items.UNIQUE)) return;
        if (stack.hasCustomHoverName()) return;

        cir.setReturnValue(UniqueNameStyler.style(cir.getReturnValue()));
    }
}
