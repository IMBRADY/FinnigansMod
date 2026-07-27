package net.finnigan.tommemod.event;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.capability.reputation.ModReputationCapabilities;
import net.finnigan.tommemod.capability.reputation.ReputationHandler;
import net.finnigan.tommemod.config.ModConfig;
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

    @SubscribeEvent
    public static void onTrade(TradeWithVillagerEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        applyChange(player, event.getAbstractVillager().blockPosition(), ModConfig.TRADE_GAIN.get());
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        // WarriorVillagerEntity isn't an AbstractVillager, but hurting it costs the same reputation
        // as hurting a regular villager - it's still one of the village's own.
        if (!(victim instanceof AbstractVillager || victim instanceof WarriorVillagerEntity)) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;

        // Skip a killing blow here; LivingDeathEvent below awards the (larger) kill penalty instead.
        if (victim.getHealth() - event.getAmount() <= 0) return;

        applyChange(player, victim.blockPosition(), ModConfig.VILLAGER_HURT_LOSS.get());
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        Entity victim = event.getEntity();
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;

        if (victim instanceof AbstractVillager || victim instanceof WarriorVillagerEntity) {
            applyChange(player, victim.blockPosition(), ModConfig.VILLAGER_KILLED_LOSS.get());
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
            }
        });
    }
}
