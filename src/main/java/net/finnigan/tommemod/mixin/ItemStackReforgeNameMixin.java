package net.finnigan.tommemod.mixin;

import net.finnigan.tommemod.reforge.Reforge;
import net.finnigan.tommemod.reforge.Reforges;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Prefixes a reforged item's name with the reforge, so a rolled sword reads "Sharp Diamond Sword".
 *
 * <p>Hooked at getHoverName for the same reason {@link ItemStackUniqueNameMixin} is: it is the one
 * place every display of an item's name passes through, so the tooltip, the hotbar popup, item frames
 * and anvils all agree without four separate hooks. Client-only, matching that mixin - names the server
 * produces (death messages, container titles) stay plain.
 *
 * <p>A stack the player has renamed keeps its custom name; the prefix is part of the item's own
 * identity, not something that should fight an anvil.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackReforgeNameMixin {

    @Inject(method = "getHoverName", at = @At("RETURN"), cancellable = true)
    private void tommemod$reforgePrefix(CallbackInfoReturnable<Component> cir) {
        ItemStack stack = (ItemStack) (Object) this;
        if (stack.hasCustomHoverName()) return;

        Reforge reforge = Reforges.get(stack);
        if (reforge == null) return;

        cir.setReturnValue(Component.translatable(reforge.nameKey())
                .append(CommonComponents.SPACE)
                .append(cir.getReturnValue()));
    }
}
