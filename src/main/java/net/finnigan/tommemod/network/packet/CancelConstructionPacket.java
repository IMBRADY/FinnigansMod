package net.finnigan.tommemod.network.packet;

import net.finnigan.tommemod.village.VillageManager;
import net.finnigan.tommemod.village.blueprint.BlueprintModeManager;
import net.finnigan.tommemod.village.construction.ConstructionManager;
import net.finnigan.tommemod.village.construction.ConstructionService;
import net.finnigan.tommemod.village.construction.ConstructionSite;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Client -> server: the planner cancelled a construction site (pressed the cancel key twice on it). */
public class CancelConstructionPacket {

    private final UUID siteId;

    public CancelConstructionPacket(UUID siteId) {
        this.siteId = siteId;
    }

    public CancelConstructionPacket(FriendlyByteBuf buf) {
        this.siteId = buf.readUUID();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(siteId);
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null || !BlueprintModeManager.isActive(player)) return;
            ServerLevel level = player.serverLevel();
            ConstructionSite site = ConstructionManager.get(level).get(siteId);
            if (site == null) return;
            UUID village = BlueprintModeManager.villageOf(player);
            if (village == null || !village.equals(site.villageId())) return;
            if (!BlueprintModeManager.mayPlan(player, VillageManager.get(level), village, BlueprintModeManager.isFree(player))) return;
            ConstructionService.cancel(level, site, player);
        });
        ctx.setPacketHandled(true);
    }
}
