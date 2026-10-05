package net.finnigan.tommemod.network.packet;

import net.finnigan.tommemod.config.ModConfig;
import net.finnigan.tommemod.village.VillageFunds;
import net.finnigan.tommemod.village.VillageManager;
import net.finnigan.tommemod.village.VillageRegion;
import net.finnigan.tommemod.village.blueprint.Blueprint;
import net.finnigan.tommemod.village.blueprint.BlueprintModeManager;
import net.finnigan.tommemod.village.blueprint.BlueprintPlanner;
import net.finnigan.tommemod.village.blueprint.Blueprints;
import net.finnigan.tommemod.village.construction.BuilderWorkHandler;
import net.finnigan.tommemod.village.construction.ConstructionManager;
import net.finnigan.tommemod.village.construction.ConstructionService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Client -> server: the planner confirmed a placement. Everything is checked again here from scratch
 * - the client's green ghost is a preview, not a promise - and only then is the cost taken and the
 * site started.
 */
public class PlaceBlueprintPacket {

    private final String blueprintId;
    private final Rotation rotation;
    private final BlockPos origin;

    public PlaceBlueprintPacket(String blueprintId, Rotation rotation, BlockPos origin) {
        this.blueprintId = blueprintId;
        this.rotation = rotation;
        this.origin = origin;
    }

    public PlaceBlueprintPacket(FriendlyByteBuf buf) {
        this.blueprintId = buf.readUtf(64);
        this.rotation = buf.readEnum(Rotation.class);
        this.origin = buf.readBlockPos();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(blueprintId, 64);
        buf.writeEnum(rotation);
        buf.writeBlockPos(origin);
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) place(player);
        });
        ctx.setPacketHandled(true);
    }

    private void place(ServerPlayer player) {
        if (!BlueprintModeManager.isActive(player)) return;
        UUID village = BlueprintModeManager.villageOf(player);
        Blueprint bp = Blueprints.get(blueprintId);
        if (village == null || bp == null) return;

        ServerLevel level = player.serverLevel();
        VillageManager villages = VillageManager.get(level);
        boolean free = BlueprintModeManager.isFree(player);
        if (!BlueprintModeManager.mayPlan(player, villages, village, free)) {
            fail(player, "You are no longer this village's Chief");
            return;
        }

        VillageRegion region = villages.resolveVillageRegion(level, village);
        BoundingBox box = BlueprintPlanner.boundsFor(bp, rotation, origin);
        double allowed = region.radius() + ModConfig.BUILDER_HUB_REGION_PADDING_BLOCKS.get();
        double dx = box.getCenter().getX() - region.anchor().getX();
        double dz = box.getCenter().getZ() - region.anchor().getZ();
        if (dx * dx + dz * dz > allowed * allowed) {
            fail(player, "Too far from the village");
            return;
        }

        ConstructionManager sites = ConstructionManager.get(level);
        int maxSites = ModConfig.BLUEPRINT_MAX_ACTIVE_SITES.get();
        if (sites.inVillage(village).size() >= maxSites) {
            fail(player, "Your builders already have " + maxSites + " buildings under way");
            return;
        }
        if (sites.overlaps(box)) {
            fail(player, "Overlaps another construction");
            return;
        }

        int builders = BuilderWorkHandler.countBuilders(level, region);
        if (builders < bp.requiredBuilders()) {
            fail(player, "The " + bp.name() + " needs " + bp.requiredBuilders() + " Builders in the village (have " + builders + ")");
            return;
        }

        BlueprintPlanner.Plan plan = BlueprintPlanner.plan(level, bp, rotation, origin, ModConfig.BLUEPRINT_MAX_GROUND_GAP.get());
        if (!plan.valid()) {
            player.displayClientMessage(plan.problem().copy().withStyle(ChatFormatting.RED), true);
            return;
        }

        List<Blueprint.Cost> paid = free ? List.of() : bp.cost();
        for (Blueprint.Cost c : paid) {
            if (!VillageFunds.hasEnough(player, c.item(), c.count())) {
                fail(player, "Not enough " + c.item().getDescription().getString() + " (" + c.count() + " needed)");
                return;
            }
        }
        for (Blueprint.Cost c : paid) VillageFunds.tryDeductItem(player, c.item(), c.count());

        ConstructionService.start(level, village, player.getUUID(), plan, paid);
        String crew = builders == 0 ? " - no Builders yet, it will wait for one" : " - your builders are on their way";
        player.displayClientMessage(Component.literal("Construction of the " + bp.name() + " has begun" + crew)
                .withStyle(builders == 0 ? ChatFormatting.GOLD : ChatFormatting.GREEN), true);
    }

    private static void fail(ServerPlayer player, String message) {
        player.displayClientMessage(Component.literal(message).withStyle(ChatFormatting.RED), true);
    }
}
