package net.finnigan.tommemod.network.packet;

import net.finnigan.tommemod.client.blueprint.BlueprintClient;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
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

    /** A finished building in the village. */
    public record BuildingInfo(UUID id, String blueprintId, Rotation rotation, BlockPos origin) {
    }

    public final int builderCount;
    public final List<SiteInfo> sites;
    public final List<BuildingInfo> buildings;
    /** Whether the village has a Bank standing, and if so what it holds (item id -> count). */
    public final boolean hasBank;
    public final Map<String, Long> bank;

    public SyncConstructionSitesPacket(int builderCount, List<SiteInfo> sites, List<BuildingInfo> buildings,
                                       boolean hasBank, Map<String, Long> bank) {
        this.builderCount = builderCount;
        this.sites = sites;
        this.buildings = buildings;
        this.hasBank = hasBank;
        this.bank = bank;
    }

    public SyncConstructionSitesPacket(FriendlyByteBuf buf) {
        this.builderCount = buf.readVarInt();
        int n = buf.readVarInt();
        this.sites = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            sites.add(new SiteInfo(buf.readUUID(), buf.readUtf(), buf.readEnum(Rotation.class), buf.readBlockPos(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
        }
        int nb = buf.readVarInt();
        this.buildings = new ArrayList<>(nb);
        for (int i = 0; i < nb; i++) {
            buildings.add(new BuildingInfo(buf.readUUID(), buf.readUtf(), buf.readEnum(Rotation.class), buf.readBlockPos()));
        }
        this.hasBank = buf.readBoolean();
        int b = buf.readVarInt();
        this.bank = new HashMap<>();
        for (int i = 0; i < b; i++) bank.put(buf.readUtf(), buf.readVarLong());
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
        buf.writeVarInt(buildings.size());
        for (BuildingInfo b : buildings) {
            buf.writeUUID(b.id());
            buf.writeUtf(b.blueprintId());
            buf.writeEnum(b.rotation());
            buf.writeBlockPos(b.origin());
        }
        buf.writeBoolean(hasBank);
        buf.writeVarInt(bank.size());
        bank.forEach((id, count) -> {
            buf.writeUtf(id);
            buf.writeVarLong(count);
        });
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> BlueprintClient.onSites(this));
        ctx.setPacketHandled(true);
    }
}
