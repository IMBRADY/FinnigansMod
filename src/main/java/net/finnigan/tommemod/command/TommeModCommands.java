package net.finnigan.tommemod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.capability.reputation.ModReputationCapabilities;
import net.finnigan.tommemod.capability.reputation.ReputationTier;
import net.finnigan.tommemod.entity.custom.ElderVillagerEntity;
import net.finnigan.tommemod.village.VillageManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;
import java.util.UUID;

/**
 * Handles the "[Become Village Chief]" clickable chat prompt sent by ElderVillagerEntity.mobInteract.
 * The click event just runs this command - since a clicked chat component can be replayed by a
 * stale or malicious client, everything is re-validated here before granting anything.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class TommeModCommands {

    private static final double MAX_CONFIRM_DISTANCE_SQR = 8.0 * 8.0;

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("tommemod")
                .then(Commands.literal("chief")
                        .then(Commands.literal("confirm")
                                .then(Commands.argument("villageId", UuidArgument.uuid())
                                        .executes(TommeModCommands::confirmChief)))));
    }

    private static int confirmChief(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        UUID villageId = UuidArgument.getUuid(ctx, "villageId");

        // Everything below is re-validated against VillageManager directly, keyed by the same
        // villageId throughout (rather than an Elder entity's own cached copy of it), so a demoted
        // Chief who regains enough reputation can always reclaim the seat once it's actually open.
        VillageManager manager = VillageManager.get(level);
        if (!manager.isEstablished(villageId)) {
            fail(player, "that village no longer exists");
            return 0;
        }

        Optional<UUID> elderUUID = manager.getElder(villageId);
        if (elderUUID.isEmpty()) {
            fail(player, "that village has no Elder to confirm with");
            return 0;
        }

        Entity elderEntity = level.getEntity(elderUUID.get());
        if (!(elderEntity instanceof ElderVillagerEntity elder) || !elder.isAlive()) {
            fail(player, "that village's Elder is no longer here");
            return 0;
        }

        if (player.distanceToSqr(elder) > MAX_CONFIRM_DISTANCE_SQR) {
            fail(player, "you've wandered too far from the Elder");
            return 0;
        }

        if (manager.getChief(villageId).isPresent()) {
            fail(player, "this village already has a Chief");
            return 0;
        }

        ReputationTier tier = player.getCapability(ModReputationCapabilities.REPUTATION_HANDLER)
                .map(handler -> handler.getTier(villageId))
                .orElse(ReputationTier.NOVICE);
        if (!tier.isAtLeast(ReputationTier.APPRENTICE)) {
            fail(player, "you no longer have enough reputation with this village");
            return 0;
        }

        if (!manager.trySetChief(villageId, player.getUUID())) {
            fail(player, "this village already has a Chief");
            return 0;
        }
        player.sendSystemMessage(Component.literal("You are now the permanent Chief of this village!"));
        return 1;
    }

    private static void fail(ServerPlayer player, String reason) {
        player.sendSystemMessage(Component.literal("That offer is no longer available (" + reason + ")."));
    }
}
