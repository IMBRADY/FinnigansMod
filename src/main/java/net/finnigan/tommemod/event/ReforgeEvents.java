package net.finnigan.tommemod.event;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.reforge.Reforge;
import net.finnigan.tommemod.reforge.Reforges;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * Wiring for the reforge system: handing out reforges, and making them do something.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public final class ReforgeEvents {

    /**
     * Ticks between inventory sweeps. Assignment is lazy on purpose - there is no single event for
     * "an item entered a player's inventory", and chasing crafting, loot, trades, fishing, bartering
     * and mob drops separately means every path anyone forgets silently yields unreforged gear. One
     * sweep catches all of them, and picks up pre-existing worlds for free.
     */
    private static final int SWEEP_INTERVAL_TICKS = 20;

    private ReforgeEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Player player = event.player;
        if (player.level().isClientSide()) return;
        if (player.tickCount % SWEEP_INTERVAL_TICKS != 0) return;

        Inventory inventory = player.getInventory();
        for (List<ItemStack> compartment : List.of(inventory.items, inventory.armor, inventory.offhand)) {
            for (ItemStack stack : compartment) {
                Reforges.ensureRolled(stack, player.getRandom());
            }
        }
    }

    /**
     * Adds the reforge's modifier to the item's own set. Hooked here rather than by overriding
     * {@code getAttributeModifiers} because most reforgeable gear is vanilla, which this mod cannot
     * override - the event is the only seam that reaches a diamond chestplate.
     */
    @SubscribeEvent
    public static void onItemAttributes(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();

        Reforge reforge = Reforges.get(stack);
        if (reforge == null || reforge.affectsDamageTaken()) return;
        if (!Reforges.appliesInSlot(stack, event.getSlotType())) return;

        Attribute attribute = reforge.attribute();
        AttributeModifier modifier = reforge.modifierFor(event.getSlotType());
        if (attribute == null || modifier == null) return;

        event.addModifier(attribute, modifier);
    }

    /**
     * Resolves Warded and Exposed, the two reforges with no attribute behind them. Every equipped slot
     * contributes, so a full set of Warded armour is 4% off rather than 1%.
     */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) return;

        float multiplier = 1.0F;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = victim.getItemBySlot(slot);

            Reforge reforge = Reforges.get(stack);
            if (reforge == null || !reforge.affectsDamageTaken()) continue;
            if (!Reforges.appliesInSlot(stack, slot)) continue;

            multiplier += (float) reforge.amount();
        }

        if (multiplier == 1.0F) return;
        event.setAmount(Math.max(0.0F, event.getAmount() * multiplier));
    }
}
