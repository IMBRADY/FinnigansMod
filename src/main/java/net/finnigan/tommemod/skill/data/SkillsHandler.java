package net.finnigan.tommemod.skill.data;

import net.finnigan.tommemod.skill.Skill;
import net.finnigan.tommemod.skill.SkillNode;
import net.finnigan.tommemod.skill.SkillProgressView;
import net.finnigan.tommemod.skill.SkillTreeManager;
import net.finnigan.tommemod.skill.effect.SkillEffect;
import net.finnigan.tommemod.skill.effect.SkillEffectTotals;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.util.INBTSerializable;

import java.util.HashMap;
import java.util.Map;

/**
 * A player's progress in every skill, and the one place levelling actually happens.
 *
 * Holds nothing about what the trees contain - only what this player has earned and bought - so it
 * survives a datapack reload that renames or removes a node. A rank recorded against a node that no
 * longer exists is simply never read; the points stay spent, which is the conservative answer, and
 * the reset command exists for when it isn't.
 */
public class SkillsHandler implements INBTSerializable<CompoundTag>, SkillProgressView {

    private final Map<ResourceLocation, SkillProgress> progress = new HashMap<>();

    /** Derived from the ranks above; never saved, always rebuilt from them. */
    private SkillEffectTotals totals = new SkillEffectTotals();

    private Runnable changeListener = () -> {
    };

    public void setChangeListener(Runnable listener) {
        this.changeListener = listener;
    }

    /**
     * Anything that alters what the player owns ends here.
     *
     * Recomputes before notifying, so that by the time a listener reacts - syncing to the client,
     * reapplying attributes - the derived totals already agree with the ranks.
     */
    public void markChanged() {
        recompute();
        changeListener.run();
    }

    /** Walks every purchased node and re-adds up what they grant. */
    public void recompute() {
        SkillEffectTotals rebuilt = new SkillEffectTotals();
        progress.forEach((skillId, state) -> {
            Skill skill = SkillTreeManager.skill(skillId);
            if (skill == null) return;

            state.getNodeRanks().forEach((nodeId, rank) -> {
                SkillNode node = skill.node(nodeId);
                if (node == null || rank <= 0) return;
                // Ranks are clamped to what the node currently allows, so lowering a node's max_rank
                // in a datapack takes effect immediately instead of leaving old buyers over-powered.
                int effectiveRank = Math.min(rank, node.maxRank());
                for (SkillEffect effect : node.effects()) {
                    effect.contribute(rebuilt, effectiveRank);
                }
            });
        });
        this.totals = rebuilt;
    }

    public SkillEffectTotals totals() {
        return totals;
    }

    /** What every purchased node adds up to for one named bonus. Zero when nothing grants it. */
    public double bonus(ResourceLocation key) {
        return totals.bonus(key);
    }

    public SkillProgress get(ResourceLocation skillId) {
        return progress.computeIfAbsent(skillId, id -> new SkillProgress());
    }

    public Map<ResourceLocation, SkillProgress> all() {
        return progress;
    }

    // ---- SkillProgressView ----

    @Override
    public int level(ResourceLocation skill) {
        return get(skill).getLevel();
    }

    @Override
    public double xp(ResourceLocation skill) {
        return get(skill).getXp();
    }

    @Override
    public int pointsSpent(ResourceLocation skill) {
        return get(skill).getPointsSpent();
    }

    @Override
    public int pointsAvailable(ResourceLocation skill) {
        return get(skill).getPointsAvailable();
    }

    @Override
    public int nodeRank(ResourceLocation skill, String nodeId) {
        return get(skill).getNodeRank(nodeId);
    }

    // ---- Levelling ----

    /**
     * Banks experience and pays out any levels it buys.
     *
     * Loops rather than levelling once, because a single large award - a command, or a kill worth
     * more than a level early on - should cash out completely rather than leaving experience stranded
     * above the threshold. Returns how many levels were gained, which is what decides whether the
     * player gets told about it.
     */
    public int addXp(ResourceLocation skillId, double amount) {
        Skill skill = SkillTreeManager.skill(skillId);
        if (skill == null || amount <= 0.0) return 0;

        SkillProgress state = get(skillId);
        if (state.getLevel() >= skill.maxLevel()) return 0;

        state.setXp(state.getXp() + amount);

        int levelsGained = 0;
        while (state.getLevel() < skill.maxLevel()) {
            double needed = skill.xpToAdvance(state.getLevel());
            if (needed <= 0.0 || state.getXp() < needed) break;

            state.setXp(state.getXp() - needed);
            state.setLevel(state.getLevel() + 1);
            state.grantPoints(skill.pointsPerLevel());
            levelsGained++;
        }

        // At the cap there is nowhere for banked experience to go, and a part-full bar that can never
        // empty reads as a bug. Flatten it.
        if (state.getLevel() >= skill.maxLevel()) state.setXp(0.0);

        markChanged();
        return levelsGained;
    }

    // ---- Persistence ----

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        CompoundTag skills = new CompoundTag();
        progress.forEach((id, state) -> {
            if (!state.isUntouched()) skills.put(id.toString(), state.save());
        });
        tag.put("Skills", skills);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        progress.clear();
        CompoundTag skills = tag.getCompound("Skills");
        for (String key : skills.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            if (id != null) progress.put(id, SkillProgress.load(skills.getCompound(key)));
        }
        recompute();
    }
}
