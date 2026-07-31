package net.finnigan.tommemod.network.packet;

import net.finnigan.tommemod.block.ModBlocks;
import net.finnigan.tommemod.block.entity.BuilderHubBlockEntity;
import net.finnigan.tommemod.village.BuildingType;
import net.finnigan.tommemod.village.VillageFunds;
import net.finnigan.tommemod.village.VillageManager;
import net.finnigan.tommemod.village.VillageRegion;
import net.finnigan.tommemod.villager.ModVillagers;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Client -> server: Village Chief spending funds + resources at the Builder Hub for a
 * BuildingType, in exchange for a tagged Construction Banner they can place to start building.
 * Everything is re-validated here (never trust the client's affordability display).
 */
public class RequestBuildingBannerPacket {

    private final BlockPos hubPos;
    private final BuildingType type;

    public RequestBuildingBannerPacket(BlockPos hubPos, BuildingType type) {
        this.hubPos = hubPos;
        this.type = type;
    }

    public RequestBuildingBannerPacket(FriendlyByteBuf buf) {
        this.hubPos = buf.readBlockPos();
        this.type = buf.readEnum(BuildingType.class);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(hubPos);
        buf.writeEnum(type);
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            if (!(player.level() instanceof ServerLevel level)) return;
            if (!(level.getBlockEntity(hubPos) instanceof BuilderHubBlockEntity hub)) return;
            if (!type.isImplemented()) return;

            VillageManager manager = VillageManager.get(level);
            UUID villageId = hub.getVillageId();
            if (villageId == null) return;
            if (!player.getUUID().equals(manager.getChief(villageId).orElse(null))) return;

            VillageRegion region = manager.resolveVillageRegion(level, villageId);
            AABB box = new AABB(region.anchor()).inflate(region.radius());
            long builderCount = level.getEntitiesOfClass(Villager.class, box).stream()
                    .filter(v -> v.getVillagerData().getProfession() == ModVillagers.BUILDER.get())
                    .count();
            if (builderCount < type.getRequiredBuilders()) {
                fail(player, "Needs " + type.getRequiredBuilders() + " Builder Villagers in the village (have " + builderCount + ")");
                return;
            }

            int emeraldCost = type.getEmeraldCost();
            Item resourceItem = type.getResourceItem();
            int resourceCost = type.getResourceCount();

            if (!VillageFunds.hasEnough(player, Items.EMERALD, emeraldCost)) {
                fail(player, "Not enough emeralds (" + emeraldCost + " needed)");
                return;
            }
            if (!VillageFunds.hasEnough(player, resourceItem, resourceCost)) {
                fail(player, "Not enough " + resourceItem.getDescription().getString() + " (" + resourceCost + " needed)");
                return;
            }

            VillageFunds.tryDeductEmeralds(player, emeraldCost);
            VillageFunds.tryDeductItem(player, resourceItem, resourceCost);

            ItemStack banner = new ItemStack(ModBlocks.CONSTRUCTION_BANNER.get());
            CompoundTag tag = new CompoundTag();
            tag.putString("BuildingType", type.name());
            tag.putUUID("VillageId", villageId);
            banner.setTag(tag);

            if (!player.getInventory().add(banner)) {
                player.drop(banner, false);
            }
        });
        ctx.setPacketHandled(true);
    }

    private static void fail(ServerPlayer player, String message) {
        player.displayClientMessage(Component.literal(message).withStyle(ChatFormatting.RED), true);
    }
}
