package net.finnigan.tommemod.event;

import net.finnigan.tommemod.capability.accessory.AccessoryHandler;
import net.finnigan.tommemod.capability.accessory.AccessoryItems;
import net.finnigan.tommemod.capability.accessory.ModCapabilities;
import net.finnigan.tommemod.item.custom.ITotemEffect;
import net.finnigan.tommemod.item.custom.totems.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = "tommemod")
public class TotemEffectEvents {

    private static int tickCounter = 0;

    // Player.dropAllDeathLoot() clears the inventory before PlayerEvent.Clone fires, even when the
    // LivingDropsEvent for each item is canceled, so the pre-death contents must be snapshotted here
    // (LivingDeathEvent fires before the loot drop) and restored on respawn instead.
    private static final Map<UUID, ListTag> pendingGreedInventory = new HashMap<>();

    @SubscribeEvent
    public static void onDeathSnapshotGreedInventory(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;
        if (player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) return;
        // Post Mortem already banked the inventory (and runs at a higher priority than this), so there
        // is nothing left to snapshot - bail out rather than spend the Greed totem for no benefit.
        if (PostMortemHandler.isHoldingInventory(player)) return;

        player.getCapability(ModCapabilities.ACCESSORY_HANDLER).ifPresent(handler -> {
            ItemStack totemStack = handler.getStackInSlot(AccessoryHandler.SLOT_TOTEM_ACCESSORY);
            if (totemStack.getItem() instanceof TotemOfGreedItem) {
                pendingGreedInventory.put(player.getUUID(), player.getInventory().save(new ListTag()));
            }
        });
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (++tickCounter % 10 != 0) return;
        Player player = event.player;
        if (player.level().isClientSide) return;

        player.getCapability(ModCapabilities.ACCESSORY_HANDLER).ifPresent(handler -> {
            ItemStack totemStack = handler.getStackInSlot(AccessoryHandler.SLOT_TOTEM_ACCESSORY);
            Item equipped = totemStack.isEmpty() ? null : totemStack.getItem();

            // Only clear a totem's effect when it is NOT the one currently equipped.
            if (!(equipped instanceof TotemOfTheSunItem)) TotemOfTheSunItem.clearModifiers(player);
            if (!(equipped instanceof TotemOfTheMoonItem)) TotemOfTheMoonItem.clearModifiers(player);
            if (!(equipped instanceof TotemOfWrathItem)) TotemOfWrathItem.clearModifiers(player);

            if (!totemStack.isEmpty() && equipped instanceof ITotemEffect totemEffect) {
                totemEffect.onPlayerTick(player, totemStack);
            }

            if (!(equipped instanceof TotemOfKinshipItem)) {
                TotemOfKinshipItem.clearAllForPlayer(player, player.level());
            }
        });
    }

    // Right-clicking a totem while it's in your hotbar equips it to the totem accessory slot,
    // just like right-clicking a chestplate equips it to the armor slot.
    @SubscribeEvent
    public static void onRightClickEquipTotem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;

        InteractionHand hand = event.getHand();
        ItemStack heldStack = player.getItemInHand(hand);
        if (!AccessoryItems.isTotemAccessory(heldStack)) return;

        player.getCapability(ModCapabilities.ACCESSORY_HANDLER).ifPresent(handler -> {
            ItemStack currentTotem = handler.getStackInSlot(AccessoryHandler.SLOT_TOTEM_ACCESSORY);
            handler.setStackInSlot(AccessoryHandler.SLOT_TOTEM_ACCESSORY, heldStack.copy());
            player.setItemInHand(hand, currentTotem);
            player.level().playSound(null, player.blockPosition(),
                    SoundEvents.ARMOR_EQUIP_GENERIC, SoundSource.PLAYERS, 1.0F, 1.0F);
        });

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
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
        ListTag savedGreedInventory = pendingGreedInventory.remove(oldPlayer.getUUID());
        boolean hadGreedTotem = savedGreedInventory != null;

        // Restoring the inventory only depends on the LivingDeathEvent snapshot above, taken while
        // oldPlayer was still fully valid - it must not be gated behind oldPlayer's capability still
        // resolving here, since that can already be invalidated by the time Clone fires post-respawn.
        if (hadGreedTotem && !keepInventory) {
            newPlayer.getInventory().load(savedGreedInventory);
        }

        oldPlayer.getCapability(ModCapabilities.ACCESSORY_HANDLER).ifPresent(oldHandler ->
                newPlayer.getCapability(ModCapabilities.ACCESSORY_HANDLER).ifPresent(newHandler -> {
                    if (keepInventory || hadGreedTotem) {
                        newHandler.deserializeNBT(oldHandler.serializeNBT());
                        if (hadGreedTotem && !keepInventory) {
                            newHandler.setStackInSlot(AccessoryHandler.SLOT_TOTEM_ACCESSORY, ItemStack.EMPTY); // consume on use
                        }
                    }
                }));
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

                int previousInvulnerableTime = target.invulnerableTime;
                target.invulnerableTime = 0;
                target.hurt(event.getSource(), event.getAmount());
                target.invulnerableTime = previousInvulnerableTime;
                if (target.level() instanceof ServerLevel serverLevel) {
                    // Play crit attack sound at target position
                    serverLevel.playSound(
                            null,
                            target.getX(), target.getY(), target.getZ(),
                            SoundEvents.ANVIL_LAND,
                            SoundSource.PLAYERS,
                            0.3F, 0.5F
                    );

                    // Spawn critical hit and sweep attack particles around the target
                    serverLevel.sendParticles(
                            ParticleTypes.DRAGON_BREATH,
                            target.getX(), target.getY(0.5), target.getZ(),
                            15, 0.3, 0.5, 0.3, 0.1
                    );
                }
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