package net.finnigan.tommemod.network.packet;

import net.finnigan.tommemod.block.ModBlocks;
import net.finnigan.tommemod.village.buildings.VaultService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.UUID;
import java.util.function.Supplier;

/** Client -> server, from the vault screen: refresh, pay in, or take out. */
public class VaultActionPacket {

    public enum Action { REFRESH, DEPOSIT_EMERALDS, DEPOSIT_HELD, WITHDRAW, WITHDRAW_ALL }

    private final BlockPos vaultPos;
    private final Action action;
    private final String itemId;

    public VaultActionPacket(BlockPos vaultPos, Action action, String itemId) {
        this.vaultPos = vaultPos;
        this.action = action;
        this.itemId = itemId;
    }

    public VaultActionPacket(FriendlyByteBuf buf) {
        this.vaultPos = buf.readBlockPos();
        this.action = buf.readEnum(Action.class);
        this.itemId = buf.readUtf(256);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(vaultPos);
        buf.writeEnum(action);
        buf.writeUtf(itemId, 256);
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null || player.distanceToSqr(vaultPos.getCenter()) > 8 * 8) return;
            if (!player.level().getBlockState(vaultPos).is(ModBlocks.VILLAGE_VAULT.get())) return;
            UUID village = VaultService.villageFor(player, vaultPos);
            if (village == null) return;
            switch (action) {
                case DEPOSIT_EMERALDS -> VaultService.depositEmeralds(player, village);
                case DEPOSIT_HELD -> VaultService.depositHeld(player, village);
                case WITHDRAW, WITHDRAW_ALL -> {
                    ResourceLocation id = ResourceLocation.tryParse(itemId);
                    Item item = id != null ? ForgeRegistries.ITEMS.getValue(id) : null;
                    if (item != null) VaultService.withdraw(player, village, item, action == Action.WITHDRAW_ALL);
                }
                default -> {
                }
            }
            VaultService.sendStatus(player, vaultPos, village, false);
        });
        ctx.setPacketHandled(true);
    }
}
