package net.finnigan.tommemod.item.custom.totems;

import net.finnigan.tommemod.capability.accessory.AccessoryHandler;
import net.finnigan.tommemod.capability.accessory.ModCapabilities;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class TotemUtil {

    private static final double TRIGGERFINGER_REDUCTION = 0.20; // -20% cooldown

    // Entities whose *next* knockback application (from the hit currently being processed) should be skipped.
    // LivingHurtEvent cancellation no longer prevents knockback in 1.20.1, since knockback is applied by
    // LivingEntity#hurt after actuallyHurt returns, so it must be suppressed separately via LivingEntityTotemMixin.
    private static final Set<UUID> SUPPRESS_NEXT_KNOCKBACK = new HashSet<>();

    public static void suppressNextKnockback(LivingEntity entity) {
        SUPPRESS_NEXT_KNOCKBACK.add(entity.getUUID());
    }

    public static boolean consumeKnockbackSuppression(LivingEntity entity) {
        return SUPPRESS_NEXT_KNOCKBACK.remove(entity.getUUID());
    }

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