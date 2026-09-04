package net.finnigan.tommemod.network.packet;

import net.finnigan.tommemod.item.custom.WitherspineItem;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server-to-owner notification that damage erased the player's banked Witherspine charge. */
public final class SyncWitherspineChargeResetPacket {
    public SyncWitherspineChargeResetPacket() {
    }

    public SyncWitherspineChargeResetPacket(FriendlyByteBuf ignored) {
    }

    public void encode(FriendlyByteBuf ignored) {
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> this::applyOnClient));
        context.setPacketHandled(true);
    }

    private void applyOnClient() {
        var player = Minecraft.getInstance().player;
        if (player == null) return;

        player.getPersistentData().remove(WitherspineItem.CHARGE_TICKS_TAG);
        if (player.isUsingItem() && player.getUseItem().getItem() instanceof WitherspineItem) {
            player.stopUsingItem();
        }
    }
}
