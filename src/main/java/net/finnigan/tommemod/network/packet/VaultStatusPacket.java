package net.finnigan.tommemod.network.packet;

import net.finnigan.tommemod.client.screen.VaultScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Server -> client: what the village bank holds, for the vault screen (opening it, or refreshing it). */
public class VaultStatusPacket {

    public record Entry(String itemId, long count) {
    }

    public final BlockPos vaultPos;
    public final boolean open;
    public final boolean canWithdraw;
    public final List<Entry> entries;

    public VaultStatusPacket(BlockPos vaultPos, boolean open, boolean canWithdraw, List<Entry> entries) {
        this.vaultPos = vaultPos;
        this.open = open;
        this.canWithdraw = canWithdraw;
        this.entries = entries;
    }

    public VaultStatusPacket(FriendlyByteBuf buf) {
        this.vaultPos = buf.readBlockPos();
        this.open = buf.readBoolean();
        this.canWithdraw = buf.readBoolean();
        int n = buf.readVarInt();
        this.entries = new ArrayList<>(n);
        for (int i = 0; i < n; i++) entries.add(new Entry(buf.readUtf(), buf.readVarLong()));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(vaultPos);
        buf.writeBoolean(open);
        buf.writeBoolean(canWithdraw);
        buf.writeVarInt(entries.size());
        for (Entry e : entries) {
            buf.writeUtf(e.itemId());
            buf.writeVarLong(e.count());
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> VaultScreen.onStatus(this));
        ctx.setPacketHandled(true);
    }
}
