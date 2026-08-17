package net.finnigan.tommemod.skill.bonus;

import net.finnigan.tommemod.skill.Skill;
import net.finnigan.tommemod.skill.SkillNode;
import net.finnigan.tommemod.skill.SkillTreeManager;
import net.finnigan.tommemod.skill.data.ModSkillCapabilities;
import net.finnigan.tommemod.skill.effect.BonusSkillEffect;
import net.finnigan.tommemod.skill.effect.SkillEffect;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Where a player's bonus is coming from, node by node.
 *
 * {@link SkillBonuses} deliberately answers only "how much", because that is the whole of what a
 * gameplay handler needs and keeping it that way is what lets bonuses move between trees freely. A
 * tooltip that has to name its sources needs the other half of the answer, so this walks the same
 * purchased nodes {@code SkillsHandler.recompute} walks and keeps the attribution instead of the sum.
 *
 * Reads the capability the same way everything else does, so it works unchanged on the client - the
 * handler and the tree definitions are both synced, which is what makes a client-side tooltip able to
 * show real numbers rather than asking the server for them.
 */
public final class SkillBonusSources {

    private SkillBonusSources() {
    }

    /**
     * One node's contribution to one bonus key.
     *
     * {@code description} is the node's own line from the tree file, so the tooltip says what the
     * datapack says rather than keeping a second copy of the wording that could drift from it.
     */
    public record Source(ResourceLocation key, String skill, String node, int rank, double amount,
                         net.minecraft.network.chat.Component description) {
    }

    /** Every purchased node feeding any of {@code keys}, largest contribution first. */
    public static List<Source> of(Player player, Collection<ResourceLocation> keys) {
        List<Source> sources = new ArrayList<>();
        if (keys.isEmpty()) return sources;

        player.getCapability(ModSkillCapabilities.SKILLS).ifPresent(handler ->
                handler.all().forEach((skillId, state) -> {
                    Skill skill = SkillTreeManager.skill(skillId);
                    if (skill == null) return;

                    state.getNodeRanks().forEach((nodeId, rank) -> {
                        SkillNode node = skill.node(nodeId);
                        if (node == null || rank <= 0) return;

                        int effectiveRank = Math.min(rank, node.maxRank());
                        for (SkillEffect effect : node.effects()) {
                            if (!(effect instanceof BonusSkillEffect bonus)) continue;
                            if (!keys.contains(bonus.key())) continue;

                            double amount = bonus.amount().at(effectiveRank);
                            if (amount == 0.0) continue;

                            sources.add(new Source(bonus.key(), skill.displayName(), node.title(),
                                    effectiveRank, amount, bonus.describe(effectiveRank)));
                        }
                    });
                }));

        sources.sort(Comparator.comparingDouble(Source::amount).reversed());
        return sources;
    }
}
