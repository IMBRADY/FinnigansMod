package net.finnigan.tommemod.event;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.capability.reputation.ModReputationCapabilities;
import net.finnigan.tommemod.capability.reputation.ReputationHandler;
import net.finnigan.tommemod.capability.reputation.ReputationTier;
import net.finnigan.tommemod.network.ModNetwork;
import net.finnigan.tommemod.network.packet.SyncReputationHudPacket;
import net.finnigan.tommemod.village.VillageManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Periodically tells each player their reputation standing in their nearest village, for the
 * inventory-screen reputation badge (see ClientReputationHud / the InventoryScreen mixins).
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class ReputationHudSyncHandler {

    private static final int INTERVAL_TICKS = 20;
    private static final Map<UUID, Integer> tickCounters = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        int counter = tickCounters.merge(player.getUUID(), 1, Integer::sum);
        if (counter < INTERVAL_TICKS) return;
        tickCounters.put(player.getUUID(), 0);

        VillageManager manager = VillageManager.get(level);
        Optional<UUID> villageId = manager.resolveVillage(level, player.blockPosition());

        boolean hasVillage = villageId.isPresent();
        int tierOrdinal = 0;
        int score = 0;
        int nextThreshold = -1;
        boolean chief = false;

        if (hasVillage) {
            UUID id = villageId.get();
            ReputationHandler handler = player.getCapability(ModReputationCapabilities.REPUTATION_HANDLER).orElse(null);
            if (handler != null) {
                score = handler.getReputation(id);
                ReputationTier tier = handler.getTier(id);
                tierOrdinal = tier.ordinal();
                nextThreshold = tier.nextThreshold();
            }
            chief = manager.getChief(id).map(c -> c.equals(player.getUUID())).orElse(false);
        }

        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new SyncReputationHudPacket(hasVillage, tierOrdinal, score, nextThreshold, chief));
    }
}
