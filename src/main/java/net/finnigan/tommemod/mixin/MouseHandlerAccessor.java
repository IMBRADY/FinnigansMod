package net.finnigan.tommemod.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes MouseHandler's private raw-cursor-delta accumulators (neither field is final, so no
 * @Mutable is needed) so MouseHandlerMixin can zero them out while the blueprint camera glides,
 * avoiding a leftover-delta camera jump once the glide ends. */
@Mixin(MouseHandler.class)
public interface MouseHandlerAccessor {

    @Accessor("accumulatedDX")
    double tommemod$getAccumulatedDX();

    @Accessor("accumulatedDX")
    void tommemod$setAccumulatedDX(double value);

    @Accessor("accumulatedDY")
    double tommemod$getAccumulatedDY();

    @Accessor("accumulatedDY")
    void tommemod$setAccumulatedDY(double value);
}
