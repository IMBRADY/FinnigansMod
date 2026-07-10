package net.finnigan.tommemod.network;

import net.finnigan.tommemod.item.custom.ModifierKeyTracker;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ConfirmKeyPacket {
    private final boolean pressed;

    public ConfirmKeyPacket(boolean pressed) {
        this.pressed = pressed;
    }

    public static void encode(ConfirmKeyPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.pressed);
    }

    public static ConfirmKeyPacket decode(FriendlyByteBuf buf) {
        return new ConfirmKeyPacket(buf.readBoolean());
    }

    public static void handle(ConfirmKeyPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var player = ctx.get().getSender();
            if (player != null) {
                ModifierKeyTracker.set(player.getUUID(), msg.pressed);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}