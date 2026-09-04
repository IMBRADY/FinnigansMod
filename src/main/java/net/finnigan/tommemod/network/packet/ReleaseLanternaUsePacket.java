package net.finnigan.tommemod.network.packet;

import net.finnigan.tommemod.item.custom.LanternaItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Clears Lanterna's post-hit latch only after the client physically releases the use key. */
public final class ReleaseLanternaUsePacket {
    public ReleaseLanternaUsePacket() {}
    public ReleaseLanternaUsePacket(FriendlyByteBuf ignored) {}
    public void encode(FriendlyByteBuf ignored) {}

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (context.getSender() != null) LanternaItem.clearReleaseLatch(context.getSender());
        });
        context.setPacketHandled(true);
    }
}
