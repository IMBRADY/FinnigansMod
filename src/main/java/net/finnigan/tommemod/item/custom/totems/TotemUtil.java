package net.finnigan.tommemod.item.custom.totems;

import net.finnigan.tommemod.capability.accessory.AccessoryHandler;
import net.finnigan.tommemod.capability.accessory.ModCapabilities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class TotemUtil {

    private static final double TRIGGERFINGER_REDUCTION = 0.20; // -20% cooldown

    /** Returns the cooldown duration to actually apply, after totem reduction if equipped. */
    public static int applyCooldownReduction(Player player, int baseCooldownTicks) {
        boolean hasTriggerfinger = player.getCapability(ModCapabilities.ACCESSORY_HANDLER)
                .map(handler -> {
                    ItemStack totemStack = handler.getStackInSlot(AccessoryHandler.SLOT_TOTEM_ACCESSORY);
                    return totemStack.getItem() instanceof TotemOfTriggerfingerItem;
                })
                .orElse(false);

        if (hasTriggerfinger) {
            return (int) Math.round(baseCooldownTicks * (1.0 - TRIGGERFINGER_REDUCTION));
        }
        return baseCooldownTicks;
    }
}