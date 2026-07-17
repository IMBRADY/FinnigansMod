package net.finnigan.tommemod.mixin;

import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Slot.class)
public interface SlotAccessor {

    @Accessor("x")
    @Mutable
    void tommemod$setX(int x);

    @Accessor("y")
    @Mutable
    void tommemod$setY(int y);
}