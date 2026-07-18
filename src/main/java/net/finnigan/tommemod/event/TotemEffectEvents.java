package net.finnigan.tommemod.event;

import net.finnigan.tommemod.capability.accessory.AccessoryHandler;
import net.finnigan.tommemod.capability.accessory.ModCapabilities;
import net.finnigan.tommemod.item.custom.ITotemEffect;
import net.finnigan.tommemod.item.custom.totems.TotemOfDoublestrikeItem;
import net.finnigan.tommemod.item.custom.totems.TotemOfTheMoonItem;
import net.finnigan.tommemod.item.custom.totems.TotemOfTheSunItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "tommemod")
public class TotemEffectEvents {

    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (++tickCounter % 10 != 0) return;
        Player player = event.player;
        if (player.level().isClientSide) return;

        player.getCapability(ModCapabilities.ACCESSORY_HANDLER).ifPresent(handler -> {
            ItemStack totemStack = handler.getStackInSlot(AccessoryHandler.SLOT_TOTEM_ACCESSORY);

            // Clear both passive totem effects first, then reapply whichever is actually equipped.
            TotemOfTheSunItem.clearModifiers(player);
            TotemOfTheMoonItem.clearModifiers(player);

            if (!totemStack.isEmpty() && totemStack.getItem() instanceof ITotemEffect totemEffect) {
                totemEffect.onPlayerTick(player, totemStack);
            }
        });
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;

        player.getCapability(ModCapabilities.ACCESSORY_HANDLER).ifPresent(handler -> {
            ItemStack totemStack = handler.getStackInSlot(AccessoryHandler.SLOT_TOTEM_ACCESSORY);
            if (!totemStack.isEmpty() && totemStack.getItem() instanceof ITotemEffect totemEffect) {
                if (totemEffect.onDamageTaken(player, totemStack, event.getSource(), event.getAmount())) {
                    event.setCanceled(true);
                }
            }
        });
    }

    @SubscribeEvent
    public static void onLivingHurtForDoublestrike(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof Player attacker)) return;
        if (attacker.level().isClientSide) return;
        if (event.getEntity() == attacker) return;

        attacker.getCapability(ModCapabilities.ACCESSORY_HANDLER).ifPresent(handler -> {
            ItemStack totemStack = handler.getStackInSlot(AccessoryHandler.SLOT_TOTEM_ACCESSORY);
            if (totemStack.getItem() instanceof TotemOfDoublestrikeItem doublestrike
                    && doublestrike.rollDoubleStrike(attacker)) {
                LivingEntity target = event.getEntity();
                target.hurt(event.getSource(), event.getAmount());
            }
        });
    }
}