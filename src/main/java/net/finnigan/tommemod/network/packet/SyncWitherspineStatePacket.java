package net.finnigan.tommemod.network.packet;

import net.finnigan.tommemod.item.custom.WitherspineItem;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server-authoritative synchronization for Witherspine's banked charge and firing pool. */
public final class SyncWitherspineStatePacket {
    private final int chargeTicks;
    private final int firingTicks;

    public SyncWitherspineStatePacket(int chargeTicks, int firingTicks) {
        this.chargeTicks = chargeTicks;
        this.firingTicks = firingTicks;
    }

    public SyncWitherspineStatePacket(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readVarInt());
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(chargeTicks);
        buffer.writeVarInt(firingTicks);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> this::applyOnClient));
        context.setPacketHandled(true);
    }

    private void applyOnClient() {
        var player = Minecraft.getInstance().player;
        if (player == null) return;

        if (chargeTicks > 0) {
            player.getPersistentData().putInt(WitherspineItem.CHARGE_TICKS_TAG, chargeTicks);
        } else {
            player.getPersistentData().remove(WitherspineItem.CHARGE_TICKS_TAG);
        }
        if (firingTicks > 0) {
            player.getPersistentData().putInt(WitherspineItem.FIRING_TICKS_LEFT_TAG, firingTicks);
        } else {
            player.getPersistentData().remove(WitherspineItem.FIRING_TICKS_LEFT_TAG);
        }
    }
}
