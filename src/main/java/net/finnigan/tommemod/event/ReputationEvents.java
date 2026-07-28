package net.finnigan.tommemod.event;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.capability.reputation.ModReputationCapabilities;
import net.finnigan.tommemod.capability.reputation.ReputationHandler;
import net.finnigan.tommemod.capability.reputation.ReputationTier;
import net.finnigan.tommemod.config.ModConfig;
import net.finnigan.tommemod.entity.custom.ElderVillagerEntity;
import net.finnigan.tommemod.entity.custom.WarriorVillagerEntity;
import net.finnigan.tommemod.village.VillageManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.TradeWithVillagerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;
import java.util.UUID;

/**
 * Awards/penalizes the mod's own villager-reputation system, mirroring vanilla's gossip categories:
 * trading (small gain), hurting/killing villagers (loss), killing an Iron Golem (loss),
 * and killing a hostile mob while inside a village (small gain).
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class ReputationEvents {

    // The Elder is the village's leader, so harming one stings twice as much reputation-wise as
    // harming a regular Villager/Warrior Villager.
    private static final int ELDER_LOSS_MULTIPLIER = 2;

    @SubscribeEvent
    public static void onTrade(TradeWithVillagerEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        applyChange(player, event.getAbstractVillager().blockPosition(), ModConfig.TRADE_GAIN.get());
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        // WarriorVillagerEntity/ElderVillagerEntity aren't AbstractVillager, but hurting them still
        // costs reputation - they're still one of the village's own.
        if (!(victim instanceof AbstractVillager || victim instanceof WarriorVillagerEntity || victim instanceof ElderVillagerEntity)) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;

        // Skip a killing blow here; LivingDeathEvent below awards the (larger) kill penalty instead.
        if (victim.getHealth() - event.getAmount() <= 0) return;

        int multiplier = victim instanceof ElderVillagerEntity ? ELDER_LOSS_MULTIPLIER : 1;
        applyChange(player, victim.blockPosition(), multiplier * ModConfig.VILLAGER_HURT_LOSS.get());
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        Entity victim = event.getEntity();
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;

        if (victim instanceof AbstractVillager || victim instanceof WarriorVillagerEntity || victim instanceof ElderVillagerEntity) {
            int multiplier = victim instanceof ElderVillagerEntity ? ELDER_LOSS_MULTIPLIER : 1;
            applyChange(player, victim.blockPosition(), multiplier * ModConfig.VILLAGER_KILLED_LOSS.get());
        } else if (victim instanceof IronGolem) {
            applyChange(player, victim.blockPosition(), ModConfig.IRON_GOLEM_KILLED_LOSS.get());
        } else if (victim instanceof Monster) {
            applyChange(player, victim.blockPosition(), ModConfig.HOSTILE_MOB_KILLED_IN_VILLAGE_GAIN.get());
        }
    }

    private static void applyChange(ServerPlayer player, BlockPos pos, int delta) {
        if (delta == 0 || !(player.level() instanceof ServerLevel level)) return;

        Optional<UUID> villageId = VillageManager.get(level).resolveVillage(level, pos);
        if (villageId.isEmpty()) return;

        player.getCapability(ModReputationCapabilities.REPUTATION_HANDLER).ifPresent(handler -> {
            ReputationHandler.ReputationChange change = handler.addReputation(villageId.get(), delta);
            if (change.tierChanged()) {
                boolean promoted = change.newTier().ordinal() > change.oldTier().ordinal();
                Component message = Component.literal(promoted ? "Reputation risen to: " : "Reputation fallen to: ")
                        .withStyle(promoted ? ChatFormatting.GREEN : ChatFormatting.RED)
                        .append(Component.literal(change.newTier().name())
                                .withStyle(promoted ? ChatFormatting.GREEN : ChatFormatting.RED));
                // The 'true' flag sends the message to the Action Bar instead of the Chat
                player.displayClientMessage(message, true);

                if (change.newTier() == ReputationTier.NOVICE) {
                    demoteChiefIfNovice(player, level, villageId.get());
                }
            }
        });
    }

    /**
     * A Chief who falls back to Novice reputation with their own village loses the title - Chief
     * status lives in VillageManager (keyed by village), so freeing it back up lets someone else earn it.
     */
    private static void demoteChiefIfNovice(ServerPlayer player, ServerLevel level, UUID villageId) {
        VillageManager manager = VillageManager.get(level);
        manager.getChief(villageId)
                .filter(chief -> chief.equals(player.getUUID()))
                .ifPresent(chief -> {
                    manager.removeChief(villageId);
                    Component message = Component.literal("Your reputation has fallen too far - you are no longer this village's Chief.")
                            .withStyle(ChatFormatting.RED);
                    player.sendSystemMessage(message);
                });
    }
}
