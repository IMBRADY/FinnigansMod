package net.finnigan.tommemod.event;

import net.finnigan.tommemod.capability.accessory.AccessoryHandler;
import net.finnigan.tommemod.capability.accessory.ModCapabilities;
import net.finnigan.tommemod.item.custom.ITotemEffect;
import net.finnigan.tommemod.item.custom.totems.*;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
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

            if (!(totemStack.getItem() instanceof TotemOfKinshipItem)) {
                TotemOfKinshipItem.clearAllForPlayer(player, player.level());
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
    public static void onUndeadLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) return;
        if (mob.level().isClientSide) return;
        if (mob instanceof WitherBoss) return; // exception — Wither stays aggressive
        if (mob.getMobType() != MobType.UNDEAD) return;
        if (!(mob.getTarget() instanceof Player player)) return;

        player.getCapability(ModCapabilities.ACCESSORY_HANDLER).ifPresent(handler -> {
            ItemStack totemStack = handler.getStackInSlot(AccessoryHandler.SLOT_TOTEM_ACCESSORY);
            if (totemStack.getItem() instanceof TotemOfTheUndeadItem) {
                mob.setTarget(null);
            }
        });
    }

    @SubscribeEvent
    public static void onGreedDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;

        player.getCapability(ModCapabilities.ACCESSORY_HANDLER).ifPresent(handler -> {
            ItemStack totemStack = handler.getStackInSlot(AccessoryHandler.SLOT_TOTEM_ACCESSORY);
            if (totemStack.getItem() instanceof TotemOfGreedItem) {
                event.setCanceled(true);
            }
        });
    }

    @SubscribeEvent
    public static void clone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) return;

        Player oldPlayer = event.getOriginal();
        Player newPlayer = event.getEntity();
        boolean keepInventory = newPlayer.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY);

        oldPlayer.getCapability(ModCapabilities.ACCESSORY_HANDLER).ifPresent(oldHandler -> {
            ItemStack totemStack = oldHandler.getStackInSlot(AccessoryHandler.SLOT_TOTEM_ACCESSORY);
            boolean hadGreedTotem = totemStack.getItem() instanceof TotemOfGreedItem;

            newPlayer.getCapability(ModCapabilities.ACCESSORY_HANDLER).ifPresent(newHandler -> {
                if (keepInventory || hadGreedTotem) {
                    newHandler.deserializeNBT(oldHandler.serializeNBT());
                    if (hadGreedTotem && !keepInventory) {
                        newHandler.setStackInSlot(AccessoryHandler.SLOT_TOTEM_ACCESSORY, ItemStack.EMPTY); // consume on use
                    }
                }
            });

            if (hadGreedTotem && !keepInventory) {
                newPlayer.getInventory().replaceWith(oldPlayer.getInventory());
            }
        });
    }

    @SubscribeEvent
    public static void onFastingDamageBoost(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof Player attacker)) return;
        if (attacker.level().isClientSide) return;

        attacker.getCapability(ModCapabilities.ACCESSORY_HANDLER).ifPresent(handler -> {
            ItemStack totemStack = handler.getStackInSlot(AccessoryHandler.SLOT_TOTEM_ACCESSORY);
            if (totemStack.getItem() instanceof TotemOfFastingItem && attacker.getFoodData().getFoodLevel() >= 20) {
                event.setAmount(event.getAmount() * 1.1F);
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

    @SubscribeEvent
    public static void onEnemyKilled(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof Player killer)) return;
        if (killer.level().isClientSide) return;
        if (event.getEntity() == killer) return;

        killer.getCapability(ModCapabilities.ACCESSORY_HANDLER).ifPresent(handler -> {
            ItemStack totemStack = handler.getStackInSlot(AccessoryHandler.SLOT_TOTEM_ACCESSORY);
            if (totemStack.getItem() instanceof TotemOfBloodlustItem) {
                killer.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 100, 2)); // Strength III (+9 dmg), 5s
                killer.heal(1.0F); // 0.5 hearts
            }
        });
    }
}