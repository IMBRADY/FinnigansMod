package net.finnigan.tommemod.network.packet;

import net.finnigan.tommemod.client.blueprint.BlueprintClient;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server -> a planning player: the village's construction sites and how far along each is. Enough
 * for the client to redraw each site's unfinished blocks itself (blueprint + rotation + corner), so
 * no block lists cross the wire.
 */
public class SyncConstructionSitesPacket {

    public record SiteInfo(UUID id, String blueprintId, Rotation rotation, BlockPos origin, int done, int total, int workers) {
    }

    public final int builderCount;
    public final List<SiteInfo> sites;

    public SyncConstructionSitesPacket(int builderCount, List<SiteInfo> sites) {
        this.builderCount = builderCount;
        this.sites = sites;
    }

    public SyncConstructionSitesPacket(FriendlyByteBuf buf) {
        this.builderCount = buf.readVarInt();
        int n = buf.readVarInt();
        this.sites = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            sites.add(new SiteInfo(buf.readUUID(), buf.readUtf(), buf.readEnum(Rotation.class), buf.readBlockPos(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(builderCount);
        buf.writeVarInt(sites.size());
        for (SiteInfo s : sites) {
            buf.writeUUID(s.id());
            buf.writeUtf(s.blueprintId());
            buf.writeEnum(s.rotation());
            buf.writeBlockPos(s.origin());
            buf.writeVarInt(s.done());
            buf.writeVarInt(s.total());
            buf.writeVarInt(s.workers());
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> BlueprintClient.onSites(this));
        ctx.setPacketHandled(true);
    }
}
