package net.finnigan.tommemod.network.packet;

import net.finnigan.tommemod.client.blueprint.BlueprintClient;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server -> the planning player: blueprint mode has started (with everything the client needs to
 * draw and check placements on its own) or has ended.
 */
public class BlueprintModeStatePacket {

    public final boolean active;
    public final UUID villageId;
    public final BlockPos regionCentre;
    public final double placeRadius;
    public final double flightRadius;
    public final double returnX;
    public final double returnY;
    public final double returnZ;
    public final float returnYaw;
    public final float returnPitch;
    public final String initialBlueprint;
    public final int maxGroundGap;
    public final boolean free;
    public final int maxSites;

    public BlueprintModeStatePacket(boolean active, UUID villageId, BlockPos regionCentre, double placeRadius, double flightRadius,
                                    double returnX, double returnY, double returnZ, float returnYaw, float returnPitch,
                                    String initialBlueprint, int maxGroundGap, boolean free, int maxSites) {
        this.active = active;
        this.villageId = villageId;
        this.regionCentre = regionCentre;
        this.placeRadius = placeRadius;
        this.flightRadius = flightRadius;
        this.returnX = returnX;
        this.returnY = returnY;
        this.returnZ = returnZ;
        this.returnYaw = returnYaw;
        this.returnPitch = returnPitch;
        this.initialBlueprint = initialBlueprint;
        this.maxGroundGap = maxGroundGap;
        this.free = free;
        this.maxSites = maxSites;
    }

    public static BlueprintModeStatePacket inactive() {
        return new BlueprintModeStatePacket(false, new UUID(0, 0), BlockPos.ZERO, 0, 0, 0, 0, 0, 0, 0, "", 0, false, 0);
    }

    public BlueprintModeStatePacket(FriendlyByteBuf buf) {
        this.active = buf.readBoolean();
        this.villageId = buf.readUUID();
        this.regionCentre = buf.readBlockPos();
        this.placeRadius = buf.readDouble();
        this.flightRadius = buf.readDouble();
        this.returnX = buf.readDouble();
        this.returnY = buf.readDouble();
        this.returnZ = buf.readDouble();
        this.returnYaw = buf.readFloat();
        this.returnPitch = buf.readFloat();
        this.initialBlueprint = buf.readUtf();
        this.maxGroundGap = buf.readVarInt();
        this.free = buf.readBoolean();
        this.maxSites = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(active);
        buf.writeUUID(villageId);
        buf.writeBlockPos(regionCentre);
        buf.writeDouble(placeRadius);
        buf.writeDouble(flightRadius);
        buf.writeDouble(returnX);
        buf.writeDouble(returnY);
        buf.writeDouble(returnZ);
        buf.writeFloat(returnYaw);
        buf.writeFloat(returnPitch);
        buf.writeUtf(initialBlueprint);
        buf.writeVarInt(maxGroundGap);
        buf.writeBoolean(free);
        buf.writeVarInt(maxSites);
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> BlueprintClient.onState(this));
        ctx.setPacketHandled(true);
    }
}
