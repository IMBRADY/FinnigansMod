package net.finnigan.tommemod.network.packet;

import net.finnigan.tommemod.block.entity.MonolithBlockEntity;
import net.finnigan.tommemod.village.VillageManager;
import net.finnigan.tommemod.village.buildings.BuildingSurvey;
import net.finnigan.tommemod.village.buildings.VillageBuildings;
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
 * Client -> server: the Chief pressed "Check buildings" on a Chief Desk. Sweeps the desk's village for
 * blueprint buildings it has no record of and puts them to work (see {@link BuildingSurvey}).
 */
public class SurveyBuildingsPacket {

    private final BlockPos pos;

    public SurveyBuildingsPacket(BlockPos pos) {
        this.pos = pos;
    }

    public SurveyBuildingsPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null || !(player.level() instanceof ServerLevel level)) return;
            if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0) return;
            if (!(level.getBlockEntity(pos) instanceof MonolithBlockEntity desk)) return;
            UUID villageId = desk.getVillageId();
            if (villageId == null) return;

            boolean chief = player.getUUID().equals(VillageManager.get(level).getChief(villageId).orElse(null));
            if (!chief && !(player.isCreative() && player.hasPermissions(2))) {
                player.displayClientMessage(Component.literal("Only the Village Chief may survey the village's buildings")
                        .withStyle(ChatFormatting.RED), true);
                return;
            }

            BuildingSurvey.Result result = BuildingSurvey.survey(level, villageId);
            if (result.found().isEmpty() && result.barracksMustered() == 0) {
                player.displayClientMessage(Component.literal("Every building in the village is already accounted for")
                        .withStyle(ChatFormatting.GRAY), true);
            } else {
                for (VillageBuildings.Building b : result.found()) {
                    player.sendSystemMessage(Component.literal("Found the " + b.displayName() + " at "
                            + b.origin.getX() + ", " + b.origin.getY() + ", " + b.origin.getZ()
                            + (b.purpose.isEmpty() ? "" : " - it is now working for the village")).withStyle(ChatFormatting.GREEN));
                }
                if (result.barracksMustered() > 0) {
                    player.sendSystemMessage(Component.literal("Mustered Warriors for " + result.barracksMustered() + " Barracks")
                            .withStyle(ChatFormatting.GREEN));
                }
            }
            desk.refresh(level);
        });
        ctx.setPacketHandled(true);
    }
}
