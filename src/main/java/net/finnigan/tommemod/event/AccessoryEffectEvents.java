package net.finnigan.tommemod.event;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.capability.accessory.AccessoryHandler;
import net.finnigan.tommemod.capability.accessory.ModCapabilities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class AccessoryEffectEvents {

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        player.getCapability(ModCapabilities.ACCESSORY_HANDLER).ifPresent(h -> {
            ItemStack totem = h.getStackInSlot(AccessoryHandler.SLOT_TOTEM_ACCESSORY);
            if (!totem.isEmpty()) {
                applyTotemBuff(player, totem);
            }
        });
    }

    private static void applyTotemBuff(Player player, ItemStack totem) {
        // Dispatch per-item. Example scaffold:
        // if (totem.is(ModItems.TOTEM_OF_HASTE.get())) {
        //     player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 40, 0, false, false, false));
        // }
    }

    // --- Block Totem of Undying specifically from sitting in the offhand slot ---
    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (event.getSlot() != net.minecraft.world.entity.EquipmentSlot.OFFHAND) return;
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) return;
        ItemStack newStack = event.getTo();
        if (newStack.is(Items.TOTEM_OF_UNDYING)) {
            // revert: put old stack back in offhand, try to return the totem to inventory, else drop it
            player.getInventory().offhand.set(0, event.getFrom());
            if (!player.getInventory().add(newStack)) {
                player.drop(newStack, false);
            }
        }
    }
}