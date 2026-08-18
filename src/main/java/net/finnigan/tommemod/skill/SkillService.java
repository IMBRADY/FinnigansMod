package net.finnigan.tommemod.skill;

import net.finnigan.tommemod.config.ModConfig;
import net.finnigan.tommemod.skill.data.ModSkillCapabilities;
import net.finnigan.tommemod.skill.data.SkillProgress;
import net.finnigan.tommemod.skill.data.SkillsHandler;
import net.finnigan.tommemod.skill.requirement.SkillContext;
import net.finnigan.tommemod.skill.requirement.SkillRequirement;
import net.finnigan.tommemod.skill.xp.SkillAction;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The server's half of the skill system: what earns experience, what a point may be spent on, and how
 * a purchase turns into something the player can feel.
 *
 * Nothing here knows any particular skill or node. Awarding is a lookup in the action index; buying
 * is a walk over whatever requirements the node happens to declare. Both would work identically
 * against a completely different set of trees.
 */
public final class SkillService {

    private SkillService() {
    }

    public static Optional<SkillsHandler> handler(Player player) {
        return player.getCapability(ModSkillCapabilities.SKILLS).resolve();
    }

    // ---- Earning ----

    /**
     * Posts an action and pays out whatever the loaded trees say it is worth.
     *
     * One action can feed several skills at once and is meant to - swimming across a lake is Agility
     * experience whether or not it is also something else - so this never stops at the first match.
     */
    public static void award(Player player, SkillAction action) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (serverPlayer.isSpectator() || !SkillTreeManager.isLoaded()) return;

        var bindings = SkillTreeManager.sourcesFor(action.id());
        if (bindings.isEmpty()) return;

        handler(serverPlayer).ifPresent(handler -> {
            double globalMultiplier = ModConfig.SKILL_XP_MULTIPLIER.get();
            boolean changed = false;

            boolean levelled = false;
            for (SkillTreeManager.SourceBinding binding : bindings) {
                double xp = binding.source().xpFor(action) * globalMultiplier;
                if (xp <= 0.0) continue;

                changed = true;
                if (handler.addXp(binding.skillId(), xp) > 0) {
                    announceLevelUp(serverPlayer, binding.skillId(), handler);
                    levelled = true;
                }
            }

            // A level is worth a packet of its own - the player has just been told about it and may
            // be opening the screen to spend the point. Ordinary experience waits for the flush.
            if (levelled) {
                SkillSync.toPlayer(serverPlayer);
            } else if (changed) {
                SkillSync.markDirty(serverPlayer);
            }
        });
    }

    private static void announceLevelUp(ServerPlayer player, ResourceLocation skillId, SkillsHandler handler) {
        Skill skill = SkillTreeManager.skill(skillId);
        if (skill == null) return;

        SkillProgress state = handler.get(skillId);
        player.displayClientMessage(Component.literal(skill.displayName() + " Level " + state.getLevel())
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal("  (" + state.getPointsAvailable() + " points to spend)")
                        .withStyle(ChatFormatting.GRAY)), true);
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP,
                SoundSource.PLAYERS, 0.4F, 1.6F);
    }

    // ---- Spending ----

    /** Why a purchase was refused, so the caller can say something useful rather than nothing. */
    public enum PurchaseResult {
        BOUGHT,
        UNKNOWN_NODE,
        ALREADY_MAXED,
        NOT_ENOUGH_POINTS,
        REQUIREMENTS_UNMET,
        /** Already committed to a different skill under the same exclusive heading. */
        CLASS_LOCKED
    }

    /** The share of a class's experience that comes back when it is abandoned. */
    public static final double CLASS_RESET_REFUND = 0.5;

    /**
     * Whether this player may still buy into the given exclusive tree.
     *
     * Committing is buying, not earning. Experience arrives simply from playing - swinging a sword is
     * Vanguard experience whether or not the player ever means to be one - so locking somebody out of
     * Ranger because they once punched something would be a decision they never made. Spending a point
     * is a decision, and it is the one that counts.
     */
    public static boolean classLock(SkillsHandler handler, ResourceLocation skillId) {
        if (!SkillTreeManager.isExclusive(skillId)) return false;

        ResourceLocation committed = handler.committedClass();
        return committed != null && !committed.equals(skillId);
    }

    /**
     * Buys one rank of a node, if the player may have it.
     *
     * Every condition is re-checked here regardless of what the client believed. The screen runs the
     * same requirements against the same definitions and greys out what it can't afford, but that is
     * a courtesy to the player, not a security boundary - this is the check that counts.
     */
    public static PurchaseResult unlock(ServerPlayer player, ResourceLocation skillId, String nodeId) {
        Skill skill = SkillTreeManager.skill(skillId);
        if (skill == null) return PurchaseResult.UNKNOWN_NODE;

        SkillNode node = skill.node(nodeId);
        if (node == null) return PurchaseResult.UNKNOWN_NODE;

        Optional<SkillsHandler> maybeHandler = handler(player);
        if (maybeHandler.isEmpty()) return PurchaseResult.UNKNOWN_NODE;
        SkillsHandler handler = maybeHandler.get();

        SkillProgress state = handler.get(skillId);
        int currentRank = state.getNodeRank(nodeId);
        if (currentRank >= node.maxRank()) return PurchaseResult.ALREADY_MAXED;

        // Checked before the points, so a player who has committed elsewhere is told what is actually
        // stopping them rather than being sent off to earn points they could never spend here.
        if (classLock(handler, skillId)) return PurchaseResult.CLASS_LOCKED;

        SkillContext context = new SkillContext(handler, skillId, nodeId);
        for (SkillRequirement requirement : node.allRequirements()) {
            if (!requirement.test(context)) return PurchaseResult.REQUIREMENTS_UNMET;
        }

        // Ahead of the points check, because the credit is what pays for this purchase. A player who
        // has just abandoned a class has nothing in the new tree to spend, so redeeming afterwards
        // would mean the credit could only ever be claimed by somebody who did not need it.
        redeemClassCredit(player, handler, skillId, node);

        int cost = node.costOf(currentRank + 1);
        if (state.getPointsAvailable() < cost) return PurchaseResult.NOT_ENOUGH_POINTS;

        state.spendPoints(cost);
        if (SkillTreeManager.isExclusive(skillId)) handler.setCommittedClass(skillId);
        state.setNodeRank(nodeId, currentRank + 1);
        handler.markChanged();
        applyAttributes(player);
        SkillSync.toPlayer(player);

        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP,
                SoundSource.PLAYERS, 0.5F, 1.2F);
        return PurchaseResult.BOUGHT;
    }

    /** Refunds a whole tree, keeping the levels that paid for it. */
    public static void respec(ServerPlayer player, ResourceLocation skillId) {
        handler(player).ifPresent(handler -> {
            handler.get(skillId).clearNodes();
            handler.markChanged();
            applyAttributes(player);
            SkillSync.toPlayer(player);
        });
    }

    // ---- Classes ----

    /**
     * Pays a reset's banked credit into the class the player is committing to.
     *
     * Only on the root - the node with no parents - because that click is the one that means "this is
     * the class I am taking". Redeeming on any purchase would spend the credit on whichever node in
     * whichever tree the player happened to buy first, which is not a choice they were asked to make.
     *
     * The commitment is recorded here rather than left to the caller, so that a credit which lands and
     * a purchase which then fails on points still leave the player as the class they picked. The credit
     * has gone into that tree; pretending they were still uncommitted would let them carry its levels
     * into a different class.
     */
    private static void redeemClassCredit(ServerPlayer player, SkillsHandler handler,
                                          ResourceLocation skillId, SkillNode node) {
        if (!SkillTreeManager.isExclusive(skillId)) return;
        if (!node.parents().isEmpty()) return;
        if (handler.classCredit() <= 0.0) return;

        double credit = handler.takeClassCredit();
        handler.setCommittedClass(skillId);
        handler.addXp(skillId, credit);

        Skill skill = SkillTreeManager.skill(skillId);
        player.displayClientMessage(Component.literal(
                        Math.round(credit) + " credited experience carried into "
                                + (skill == null ? skillId.getPath() : skill.displayName()))
                .withStyle(ChatFormatting.AQUA), false);
    }

    /**
     * Abandons whichever class the player committed to, banking a share of what they earned in it.
     *
     * Every exclusive tree is wiped, not only the committed one, because experience accumulates in all
     * of them from ordinary play - leaving Ranger sitting at level 30 while its owner walked away from
     * Vanguard would hand them the second class most of the way built. What comes back is
     * {@link #CLASS_RESET_REFUND} of the experience banked in the committed class alone, which is the
     * one they actually chose and invested in.
     *
     * Returns the credit banked, or zero if there was no class to leave.
     */
    public static double resetClass(ServerPlayer player) {
        Optional<SkillsHandler> maybeHandler = handler(player);
        if (maybeHandler.isEmpty()) return 0.0;
        SkillsHandler handler = maybeHandler.get();

        ResourceLocation committed = handler.committedClass();
        if (committed == null) return 0.0;

        double credit = totalXpIn(handler, committed) * CLASS_RESET_REFUND;

        for (Skill skill : SkillTreeManager.exclusiveGroupOf(committed)) {
            handler.get(skill.id()).reset();
        }
        handler.setCommittedClass(null);
        handler.addClassCredit(credit);

        handler.markChanged();
        applyAttributes(player);
        SkillSync.toPlayer(player);

        player.level().playSound(null, player.blockPosition(), SoundEvents.ANVIL_USE,
                SoundSource.PLAYERS, 0.5F, 1.4F);
        return credit;
    }

    /**
     * Everything a player has ever banked in one skill: the cost of every level they climbed, plus
     * whatever is sitting part-way toward the next one.
     *
     * Summed from the curve rather than tracked as a running total, so it stays correct across a
     * retune - a datapack that makes a class cheaper refunds against the cheaper curve, which is the
     * curve the player would be climbing again.
     */
    private static double totalXpIn(SkillsHandler handler, ResourceLocation skillId) {
        Skill skill = SkillTreeManager.skill(skillId);
        if (skill == null) return 0.0;

        SkillProgress state = handler.get(skillId);
        double total = state.getXp();
        for (int level = 1; level < state.getLevel(); level++) {
            total += skill.xpToAdvance(level);
        }
        return total;
    }

    // ---- Applying ----

    /**
     * Rewrites the player's skill-granted attribute modifiers from scratch.
     *
     * Clear-then-reapply rather than diffing, because the alternative is tracking which modifiers were
     * granted when - state that goes stale the moment a datapack reload changes what a node grants.
     * Modifiers are transient, so none of this is ever written to the player's NBT; a saved one would
     * stack on itself every load and could never be taken back off.
     */
    public static void applyAttributes(Player player) {
        handler(player).ifPresent(handler -> {
            Map<Attribute, Map<AttributeModifier.Operation, Double>> totals = handler.totals().attributes();

            for (Attribute attribute : SkillTreeManager.touchedAttributes()) {
                AttributeInstance instance = player.getAttribute(attribute);
                if (instance == null) continue;

                Map<AttributeModifier.Operation, Double> wanted = totals.get(attribute);
                for (AttributeModifier.Operation operation : AttributeModifier.Operation.values()) {
                    UUID id = modifierId(attribute, operation);
                    if (instance.getModifier(id) != null) instance.removeModifier(id);

                    double amount = wanted == null ? 0.0 : wanted.getOrDefault(operation, 0.0);
                    if (amount == 0.0) continue;

                    instance.addTransientModifier(new AttributeModifier(id,
                            "Skill " + operation.name().toLowerCase(java.util.Locale.ROOT), amount, operation));
                }
            }

            // Buying max health leaves the player at their old health with a longer bar. Handing the
            // headroom straight over is what makes the purchase visibly do something.
            if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
        });
    }

    /**
     * A stable id per attribute and operation.
     *
     * Derived from the names rather than hardcoded so that a datapack introducing a node on an
     * attribute the mod never touched still gets a modifier that can be found and removed again.
     */
    private static UUID modifierId(Attribute attribute, AttributeModifier.Operation operation) {
        ResourceLocation key = ForgeRegistries.ATTRIBUTES.getKey(attribute);
        String seed = "tommemod:skill:" + (key == null ? attribute.getDescriptionId() : key) + ":" + operation.name();
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }
}
