package net.finnigan.tommemod.network.packet;

import net.finnigan.tommemod.village.blueprint.BlueprintModeManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: the camera has glided back down; put the player back on their feet. */
public class ExitBlueprintModePacket {

    public ExitBlueprintModePacket() {
    }

    public ExitBlueprintModePacket(FriendlyByteBuf buf) {
    }

    public void encode(FriendlyByteBuf buf) {
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) BlueprintModeManager.exit(player);
        });
        ctx.setPacketHandled(true);
    }
}
