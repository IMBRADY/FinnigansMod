package net.finnigan.tommemod.command;

import com.mojang.brigadier.CommandDispatcher;
import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.capability.reputation.ModReputationCapabilities;
import net.finnigan.tommemod.village.VillageManager;
import net.finnigan.tommemod.village.VillageRegion;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;
import java.util.UUID;

/**
 * Temporary debug commands used to verify the village resolver / reputation capability in-game
 * during implementation. Not part of the player-facing feature set.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class TommeModDebugCommands {

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("tommemoddebug")
                .then(Commands.literal("village").executes(TommeModDebugCommands::runVillage))
                .then(Commands.literal("reputation").executes(TommeModDebugCommands::runReputation)));
    }

    private static int runReputation(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        BlockPos pos = player.blockPosition();

        VillageManager manager = VillageManager.get(level);
        Optional<UUID> villageId = manager.resolveVillage(level, pos);
        if (villageId.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("No village resolved near " + pos.toShortString()), false);
            return 0;
        }

        int score = player.getCapability(ModReputationCapabilities.REPUTATION_HANDLER)
                .map(h -> h.getReputation(villageId.get())).orElse(0);
        String tier = player.getCapability(ModReputationCapabilities.REPUTATION_HANDLER)
                .map(h -> h.getTier(villageId.get()).name()).orElse("UNKNOWN");
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Village " + villageId.get() + ": reputation=" + score + " tier=" + tier), false);
        return 1;
    }

    private static int runVillage(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        BlockPos pos = player.blockPosition();

        VillageManager manager = VillageManager.get(level);
        Optional<UUID> villageId = manager.resolveVillage(level, pos);

        if (villageId.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("No village resolved near " + pos.toShortString()), false);
            return 0;
        }

        VillageRegion region = manager.resolveVillageRegion(level, villageId.get());
        Optional<UUID> elder = manager.getElder(villageId.get());
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Village " + villageId.get() + " anchor=" + region.anchor().toShortString()
                        + " radius=" + region.radius() + " elder=" + elder.map(UUID::toString).orElse("none")), false);
        return 1;
    }
}
