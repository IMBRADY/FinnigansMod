package net.finnigan.tommemod.network.packet;

import net.finnigan.tommemod.entity.custom.GrapplingHookSupport;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Synchronizes A/D steering for every weapon using the shared grappling support. */
public final class GrappleSwingInputPacket {
    private final float input;

    public GrappleSwingInputPacket(float input) {
        this.input = Math.max(-1.0F, Math.min(1.0F, input));
    }

    public GrappleSwingInputPacket(FriendlyByteBuf buffer) {
        this(buffer.readFloat());
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeFloat(input);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (context.getSender() != null) {
                GrapplingHookSupport.setSwingInput(context.getSender(), input);
            }
        });
        context.setPacketHandled(true);
    }
}
