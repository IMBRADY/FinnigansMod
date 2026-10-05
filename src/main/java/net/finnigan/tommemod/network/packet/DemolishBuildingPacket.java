package net.finnigan.tommemod.network.packet;

import net.finnigan.tommemod.village.VillageManager;
import net.finnigan.tommemod.village.blueprint.BlueprintModeManager;
import net.finnigan.tommemod.village.buildings.BuildingDemolition;
import net.finnigan.tommemod.village.buildings.VillageBuildings;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Client -> server: the planner demolished a finished building (pressed the cancel key twice on it). */
public class DemolishBuildingPacket {

    private final UUID buildingId;

    public DemolishBuildingPacket(UUID buildingId) {
        this.buildingId = buildingId;
    }

    public DemolishBuildingPacket(FriendlyByteBuf buf) {
        this.buildingId = buf.readUUID();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(buildingId);
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null || !BlueprintModeManager.isActive(player)) return;
            ServerLevel level = player.serverLevel();
            UUID village = BlueprintModeManager.villageOf(player);
            if (village == null) return;
            if (!BlueprintModeManager.mayPlan(player, VillageManager.get(level), village, BlueprintModeManager.isFree(player))) return;
            VillageBuildings data = VillageBuildings.get(level);
            VillageBuildings.Building building = data.get(buildingId);
            if (building == null || !data.inVillage(level, village).contains(building)) return;
            BuildingDemolition.demolish(level, building, player);
        });
        ctx.setPacketHandled(true);
    }
}
