package net.finnigan.tommemod.network.packet;

import net.finnigan.tommemod.entity.custom.BallistaEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client -> server: the player manning a Ballista is holding the attack key.
 *
 * Carries nothing at all. Which Ballista is being fired is whichever one the sender is sitting in,
 * read server-side - a client that says otherwise has nothing to say. Rate is likewise the server's
 * business: the client asks every tick the key is down, and all but one request per reload cycle is
 * refused by the weapon still being wound.
 */
public class BallistaFirePacket {

    public BallistaFirePacket() {
    }

    public BallistaFirePacket(FriendlyByteBuf buf) {
    }

    public void encode(FriendlyByteBuf buf) {
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            if (!(player.getVehicle() instanceof BallistaEntity ballista)) return;

            ballista.fireForRider(player);
        });
        ctx.setPacketHandled(true);
    }
}
