package net.finnigan.tommemod.network.packet;

import net.finnigan.tommemod.block.entity.MonolithBlockEntity;
import net.finnigan.tommemod.config.ModConfig;
import net.finnigan.tommemod.village.VillageManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Client -> server: Village Chief spending emeralds to raise their village's Farm Efficiency
 * level from the Monolith's Village Upgrades tab.
 */
public class MonolithUpgradePacket {

    private final BlockPos pos;

    public MonolithUpgradePacket(BlockPos pos) {
        this.pos = pos;
    }

    public MonolithUpgradePacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            if (!(player.level() instanceof ServerLevel level)) return;
            if (!(level.getBlockEntity(pos) instanceof MonolithBlockEntity monolith)) return;

            VillageManager manager = VillageManager.get(level);
            UUID villageId = monolith.getVillageId();
            if (villageId == null) return;
            if (!player.getUUID().equals(manager.getChief(villageId).orElse(null))) return;

            int currentLevel = manager.getFarmEfficiencyLevel(villageId);
            int maxLevel = ModConfig.FARM_EFFICIENCY_MAX_LEVEL.get();
            if (currentLevel >= maxLevel) return;

            List<? extends Integer> costs = ModConfig.FARM_EFFICIENCY_UPGRADE_COST_EMERALDS.get();
            int cost = costs.isEmpty() ? 0 : costs.get(Math.min(currentLevel, costs.size() - 1));

            // No Bank building exists yet for villages to draw shared funds from - once one does,
            // try deducting from the village's bank inventory here before falling back to the
            // Chief's own inventory below.
            if (!deductEmeralds(player, cost)) {
                player.displayClientMessage(
                        Component.literal("Not enough emeralds (" + cost + " needed)").withStyle(ChatFormatting.RED),
                        true);
                return;
            }

            manager.setFarmEfficiencyLevel(villageId, currentLevel + 1);
            monolith.refresh(level);
        });
        ctx.setPacketHandled(true);
    }

    private static boolean deductEmeralds(ServerPlayer player, int cost) {
        if (cost <= 0) return true;

        int available = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(Items.EMERALD)) available += stack.getCount();
        }
        if (available < cost) return false;

        int remaining = cost;
        for (ItemStack stack : player.getInventory().items) {
            if (remaining <= 0) break;
            if (!stack.is(Items.EMERALD)) continue;
            int taken = Math.min(remaining, stack.getCount());
            stack.shrink(taken);
            remaining -= taken;
        }
        return true;
    }
}
