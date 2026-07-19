package net.finnigan.tommemod.mixin;

import net.finnigan.tommemod.capability.accessory.AccessoryHandler;
import net.finnigan.tommemod.capability.accessory.ModCapabilities;
import net.finnigan.tommemod.item.custom.totems.TotemOfFastingItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Player.class)
public abstract class PlayerExhaustionMixin {

    @ModifyVariable(method = "causeFoodExhaustion", at = @At("HEAD"), argsOnly = true)
    private float tommemod$reduceExhaustionForFasting(float exhaustion) {
        Player self = (Player) (Object) this;
        if (self.level().isClientSide) return exhaustion;

        return self.getCapability(ModCapabilities.ACCESSORY_HANDLER).map(handler -> {
            ItemStack totemStack = handler.getStackInSlot(AccessoryHandler.SLOT_TOTEM_ACCESSORY);
            if (totemStack.getItem() instanceof TotemOfFastingItem) {
                return exhaustion * 0.65F; // -35% exhaustion gain
            }
            return exhaustion;
        }).orElse(exhaustion);
    }
}