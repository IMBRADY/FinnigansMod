package net.finnigan.tommemod.network.packet;

import net.finnigan.tommemod.client.blueprint.BlueprintStandScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Server -> client: the village's construction progress as seen from a Blueprint Stand. With
 * {@code open} set it opens the stand's screen; without, it refreshes a screen already open on it.
 */
public class BlueprintStandStatusPacket {

    public record Site(String name, int done, int total, int workers) {
    }

    public final BlockPos standPos;
    public final boolean open;
    public final boolean hasVillage;
    public final boolean canPlan;
    public final int builders;
    public final int maxSites;
    public final List<Site> sites;

    public BlueprintStandStatusPacket(BlockPos standPos, boolean open, boolean hasVillage, boolean canPlan,
                                      int builders, int maxSites, List<Site> sites) {
        this.standPos = standPos;
        this.open = open;
        this.hasVillage = hasVillage;
        this.canPlan = canPlan;
        this.builders = builders;
        this.maxSites = maxSites;
        this.sites = sites;
    }

    public BlueprintStandStatusPacket(FriendlyByteBuf buf) {
        this.standPos = buf.readBlockPos();
        this.open = buf.readBoolean();
        this.hasVillage = buf.readBoolean();
        this.canPlan = buf.readBoolean();
        this.builders = buf.readVarInt();
        this.maxSites = buf.readVarInt();
        int n = buf.readVarInt();
        this.sites = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            sites.add(new Site(buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(standPos);
        buf.writeBoolean(open);
        buf.writeBoolean(hasVillage);
        buf.writeBoolean(canPlan);
        buf.writeVarInt(builders);
        buf.writeVarInt(maxSites);
        buf.writeVarInt(sites.size());
        for (Site s : sites) {
            buf.writeUtf(s.name());
            buf.writeVarInt(s.done());
            buf.writeVarInt(s.total());
            buf.writeVarInt(s.workers());
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> BlueprintStandScreen.onStatus(this));
        ctx.setPacketHandled(true);
    }
}
