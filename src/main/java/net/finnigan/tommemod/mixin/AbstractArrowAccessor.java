package net.finnigan.tommemod.mixin;

import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes what an arrow would be picked back up as.
 *
 * Archery's Thrift hands the arrow back rather than declining to take it, because by the time an arrow
 * exists the bow has already spent the ammunition. Asking the arrow what it is worth is the only way to
 * refund the right thing - guessing "a plain arrow" would quietly turn every tipped and spectral arrow
 * into a downgrade every time the node paid out.
 */
@Mixin(AbstractArrow.class)
public interface AbstractArrowAccessor {

    @Invoker("getPickupItem")
    ItemStack tommemod$getPickupItem();
}
