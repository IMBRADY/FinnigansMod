package net.finnigan.tommemod.network.packet;

import net.finnigan.tommemod.block.ModBlocks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Sent client->server when the player finishes rotating a pending construction banner (releasing
 * shift+right-click while holding one - see client.ConstructionRotationInputHandler). Stamps the
 * chosen facing onto whichever hand currently holds the banner, read later by
 * ConstructionBannerBlock#setPlacedBy at actual placement time.
 */
public class RotateConstructionBannerPacket {
    private final Direction facing;

    public RotateConstructionBannerPacket(Direction facing) {
        this.facing = facing;
    }

    public static void encode(RotateConstructionBannerPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.facing);
    }

    public static RotateConstructionBannerPacket decode(FriendlyByteBuf buf) {
        return new RotateConstructionBannerPacket(buf.readEnum(Direction.class));
    }

    public static void handle(RotateConstructionBannerPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ItemStack stack = player.getMainHandItem();
            if (stack.isEmpty() || stack.getItem() != ModBlocks.CONSTRUCTION_BANNER.get().asItem()) {
                stack = player.getOffhandItem();
            }
            if (stack.isEmpty() || stack.getItem() != ModBlocks.CONSTRUCTION_BANNER.get().asItem()) return;

            stack.getOrCreateTag().putString("Facing", msg.facing.getName());
        });
        ctx.get().setPacketHandled(true);
    }
}
