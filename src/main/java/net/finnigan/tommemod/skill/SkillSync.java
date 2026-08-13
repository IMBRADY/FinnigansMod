package net.finnigan.tommemod.skill;

import net.finnigan.tommemod.network.ModNetwork;
import net.finnigan.tommemod.network.packet.SyncSkillDataPacket;
import net.finnigan.tommemod.network.packet.SyncSkillDefinitionsPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The two things a client needs to be told about: what the trees are, and where it stands in them.
 *
 * Progress is coalesced rather than sent as it changes. A running player earns Agility experience
 * several times a second, and a packet per award would be a steady stream of traffic per player to
 * move a progress bar nobody is looking at most of the time. Marking dirty and flushing on a fixed
 * interval collapses all of that into one packet carrying the latest state, which is the only state
 * that was ever wanted. Anything the player is waiting on - a purchase, a level - sends immediately.
 */
public final class SkillSync {

    /** Twice a second: fast enough that an open screen feels live, slow enough to be nothing. */
    private static final int FLUSH_INTERVAL_TICKS = 10;

    private static final Set<UUID> DIRTY = new HashSet<>();
    private static int tickCounter;

    private SkillSync() {
    }

    /** Notes that a player's progress has moved, to be sent on the next flush. */
    public static void markDirty(ServerPlayer player) {
        DIRTY.add(player.getUUID());
    }

    /** Sends a player's progress right now, and clears any pending flush for them. */
    public static void toPlayer(ServerPlayer player) {
        DIRTY.remove(player.getUUID());
        SkillService.handler(player).ifPresent(handler ->
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                        new SyncSkillDataPacket(handler.serializeNBT())));
    }

    public static void definitionsTo(ServerPlayer player) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new SyncSkillDefinitionsPacket(SkillTreeManager.rawCategories(), SkillTreeManager.rawSkills()));
    }

    /** Called once a server tick; sends whatever has piled up. */
    public static void tick(MinecraftServer server) {
        if (++tickCounter < FLUSH_INTERVAL_TICKS) return;
        tickCounter = 0;
        if (DIRTY.isEmpty()) return;

        Set<UUID> pending = Set.copyOf(DIRTY);
        DIRTY.clear();
        for (UUID id : pending) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) toPlayer(player);
        }
    }

    public static void forget(ServerPlayer player) {
        DIRTY.remove(player.getUUID());
    }
}
