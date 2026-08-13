package net.finnigan.tommemod.network.packet;

import net.finnigan.tommemod.block.entity.MonolithBlockEntity;
import net.finnigan.tommemod.village.VillageFunds;
import net.finnigan.tommemod.village.VillageManager;
import net.finnigan.tommemod.village.VillageUpgrade;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Client -> server: Village Chief buying a level of one of their village's Chief Desk upgrades.
 *
 * Carries only which desk and which upgrade; the price is looked up server-side from
 * {@link VillageUpgrade} rather than sent, so a doctored packet can't name its own price.
 */
public class MonolithUpgradePacket {

    private final BlockPos pos;
    private final int upgradeId;

    public MonolithUpgradePacket(BlockPos pos, VillageUpgrade upgrade) {
        this.pos = pos;
        this.upgradeId = upgrade.ordinal();
    }

    public MonolithUpgradePacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.upgradeId = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeVarInt(upgradeId);
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            if (!(player.level() instanceof ServerLevel level)) return;
            if (!(level.getBlockEntity(pos) instanceof MonolithBlockEntity monolith)) return;

            VillageUpgrade upgrade = VillageUpgrade.byId(upgradeId);
            if (upgrade == null) return;

            VillageManager manager = VillageManager.get(level);
            UUID villageId = monolith.getVillageId();
            if (villageId == null) return;
            if (!player.getUUID().equals(manager.getChief(villageId).orElse(null))) return;

            int currentLevel = upgrade.levelIn(manager, villageId);
            if (currentLevel >= upgrade.maxLevel()) return;

            int cost = upgrade.costOfNextLevel(currentLevel);
            if (!VillageFunds.tryDeductItem(player, upgrade.costItem(), cost)) {
                player.displayClientMessage(
                        Component.literal("Not enough " + upgrade.costItemPlural() + " (" + cost + " needed)")
                                .withStyle(ChatFormatting.RED),
                        true);
                return;
            }

            upgrade.setLevelIn(manager, villageId, currentLevel + 1);
            monolith.refresh(level);
        });
        ctx.setPacketHandled(true);
    }
}
