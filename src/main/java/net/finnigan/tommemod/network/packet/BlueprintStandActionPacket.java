package net.finnigan.tommemod.network.packet;

import net.finnigan.tommemod.block.ModBlocks;
import net.finnigan.tommemod.village.blueprint.BlueprintModeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server, from the Blueprint Stand screen: refresh the progress shown, or enter blueprint mode. */
public class BlueprintStandActionPacket {

    public enum Action { REFRESH, ENTER }

    private final BlockPos standPos;
    private final Action action;

    public BlueprintStandActionPacket(BlockPos standPos, Action action) {
        this.standPos = standPos;
        this.action = action;
    }

    public BlueprintStandActionPacket(FriendlyByteBuf buf) {
        this.standPos = buf.readBlockPos();
        this.action = buf.readEnum(Action.class);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(standPos);
        buf.writeEnum(action);
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            // Must still be standing at an actual stand, not acting on one from across the map.
            if (player.distanceToSqr(standPos.getCenter()) > 8 * 8) return;
            if (!player.level().getBlockState(standPos).is(ModBlocks.BLUEPRINT_STAND.get())) return;
            switch (action) {
                case REFRESH -> BlueprintModeManager.sendStandStatus(player, standPos, false);
                case ENTER -> BlueprintModeManager.tryEnter(player, standPos, null);
            }
        });
        ctx.setPacketHandled(true);
    }
}
