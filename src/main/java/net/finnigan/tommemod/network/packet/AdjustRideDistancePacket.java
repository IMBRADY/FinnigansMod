package net.finnigan.tommemod.network.packet;

import net.finnigan.tommemod.event.RideStickManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class AdjustRideDistancePacket {
    private final double scrollDelta;

    public AdjustRideDistancePacket(double scrollDelta) {
        this.scrollDelta = scrollDelta;
    }

    public static void encode(AdjustRideDistancePacket msg, FriendlyByteBuf buf) {
        buf.writeDouble(msg.scrollDelta);
    }

    public static AdjustRideDistancePacket decode(FriendlyByteBuf buf) {
        return new AdjustRideDistancePacket(buf.readDouble());
    }

    public static void handle(AdjustRideDistancePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                RideStickManager.adjustDistance(player, msg.scrollDelta);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
