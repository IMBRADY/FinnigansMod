package net.finnigan.tommemod.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.skill.Skill;
import net.finnigan.tommemod.skill.SkillService;
import net.finnigan.tommemod.skill.SkillSync;
import net.finnigan.tommemod.skill.SkillTreeManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Operator commands for the skill system, so a tree can be tested without playing to the level that
 * unlocks it. Everything here goes through the same SkillService the game does - there is no path
 * that writes a rank without the requirements being checked, because a tree that can only be tested
 * by bypassing its own rules isn't being tested.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class SkillCommands {

    private static final SuggestionProvider<CommandSourceStack> SKILL_IDS = (context, builder) ->
            SharedSuggestionProvider.suggestResource(
                    SkillTreeManager.skills().stream().map(Skill::id).toList(), builder);

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("tommemod")
                .then(Commands.literal("skill").requires(source -> source.hasPermission(2))
                        .then(Commands.literal("addxp")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .then(Commands.argument("skill", ResourceLocationArgument.id())
                                                .suggests(SKILL_IDS)
                                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0))
                                                        .executes(SkillCommands::addXp)))))
                        .then(Commands.literal("points")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .then(Commands.argument("skill", ResourceLocationArgument.id())
                                                .suggests(SKILL_IDS)
                                                .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                                        .executes(SkillCommands::grantPoints)))))
                        .then(Commands.literal("respec")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .then(Commands.argument("skill", ResourceLocationArgument.id())
                                                .suggests(SKILL_IDS)
                                                .executes(SkillCommands::respec))))
                        .then(Commands.literal("query")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(SkillCommands::query)))));
    }

    private static int addXp(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ResourceLocation skillId = ResourceLocationArgument.getId(context, "skill");
        double amount = DoubleArgumentType.getDouble(context, "amount");
        if (missingSkill(context, skillId)) return 0;

        int touched = 0;
        for (ServerPlayer player : EntityArgument.getPlayers(context, "targets")) {
            SkillService.handler(player).ifPresent(handler -> {
                handler.addXp(skillId, amount);
                SkillSync.toPlayer(player);
            });
            touched++;
        }
        context.getSource().sendSuccess(() -> Component.literal(
                "Granted " + amount + " " + skillId.getPath() + " experience"), true);
        return touched;
    }

    private static int grantPoints(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ResourceLocation skillId = ResourceLocationArgument.getId(context, "skill");
        int amount = IntegerArgumentType.getInteger(context, "amount");
        if (missingSkill(context, skillId)) return 0;

        int touched = 0;
        for (ServerPlayer player : EntityArgument.getPlayers(context, "targets")) {
            SkillService.handler(player).ifPresent(handler -> {
                handler.get(skillId).grantPoints(amount);
                handler.markChanged();
                SkillSync.toPlayer(player);
            });
            touched++;
        }
        context.getSource().sendSuccess(() -> Component.literal(
                "Granted " + amount + " " + skillId.getPath() + " points"), true);
        return touched;
    }

    private static int respec(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ResourceLocation skillId = ResourceLocationArgument.getId(context, "skill");
        if (missingSkill(context, skillId)) return 0;

        int touched = 0;
        for (ServerPlayer player : EntityArgument.getPlayers(context, "targets")) {
            SkillService.respec(player, skillId);
            touched++;
        }
        context.getSource().sendSuccess(() -> Component.literal(
                "Refunded every point spent in " + skillId.getPath()), true);
        return touched;
    }

    private static int query(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        for (ServerPlayer player : EntityArgument.getPlayers(context, "targets")) {
            SkillService.handler(player).ifPresent(handler -> {
                context.getSource().sendSuccess(() ->
                        Component.literal(player.getGameProfile().getName()).withStyle(ChatFormatting.GOLD), false);
                for (Skill skill : SkillTreeManager.orderedSkills()) {
                    int level = handler.level(skill.id());
                    int available = handler.pointsAvailable(skill.id());
                    int spent = handler.pointsSpent(skill.id());
                    if (level <= 1 && available == 0 && spent == 0) continue;

                    context.getSource().sendSuccess(() -> Component.literal(
                            "  " + skill.displayName() + " Lv " + level
                                    + " - " + available + " unspent, " + spent + " spent"), false);
                }
            });
        }
        return 1;
    }

    private static boolean missingSkill(CommandContext<CommandSourceStack> context, ResourceLocation skillId) {
        if (SkillTreeManager.skill(skillId) != null) return false;

        context.getSource().sendFailure(Component.literal("No such skill: " + skillId));
        return true;
    }

}
