package net.finnigan.tommemod.network;

import net.finnigan.tommemod.client.ClientRotationHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SetPlayerRotationPacket {
    private final float yaw;
    private final float pitch;

    public SetPlayerRotationPacket(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public static void encode(SetPlayerRotationPacket msg, FriendlyByteBuf buf) {
        buf.writeFloat(msg.yaw);
        buf.writeFloat(msg.pitch);
    }

    public static SetPlayerRotationPacket decode(FriendlyByteBuf buf) {
        return new SetPlayerRotationPacket(buf.readFloat(), buf.readFloat());
    }

    public static void handle(SetPlayerRotationPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientRotationHandler.apply(msg.yaw, msg.pitch))
        );
        ctx.get().setPacketHandled(true);
    }
}