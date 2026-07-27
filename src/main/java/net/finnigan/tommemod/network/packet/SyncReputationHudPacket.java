package net.finnigan.tommemod.network.packet;

import net.finnigan.tommemod.client.ClientReputationHud;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> owning client only: the player's reputation standing in whatever village they're
 * currently nearest to, for the inventory-screen reputation badge. Sent periodically by
 * ReputationHudSyncHandler rather than only on change, since it's cheap and keeps things simple.
 */
public class SyncReputationHudPacket {

    private final boolean hasVillage;
    private final int tierOrdinal;
    private final int score;
    private final int nextThreshold;
    private final boolean chief;

    public SyncReputationHudPacket(boolean hasVillage, int tierOrdinal, int score, int nextThreshold, boolean chief) {
        this.hasVillage = hasVillage;
        this.tierOrdinal = tierOrdinal;
        this.score = score;
        this.nextThreshold = nextThreshold;
        this.chief = chief;
    }

    public SyncReputationHudPacket(FriendlyByteBuf buf) {
        this.hasVillage = buf.readBoolean();
        this.tierOrdinal = buf.readVarInt();
        this.score = buf.readVarInt();
        this.nextThreshold = buf.readVarInt();
        this.chief = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(hasVillage);
        buf.writeVarInt(tierOrdinal);
        buf.writeVarInt(score);
        buf.writeVarInt(nextThreshold);
        buf.writeBoolean(chief);
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> ClientReputationHud.update(hasVillage, tierOrdinal, score, nextThreshold, chief));
        ctx.setPacketHandled(true);
    }
}
