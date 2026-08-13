package net.finnigan.tommemod.skill.data;

import net.minecraft.nbt.CompoundTag;

import java.util.HashMap;
import java.util.Map;

/** One player's standing in one skill. */
public class SkillProgress {

    /** Experience banked toward the next level - reset, not accumulated, on each level up. */
    private double xp;
    private int level = 1;
    /**
     * Points handed out by levelling, tracked rather than derived.
     *
     * Derived from the level it would silently change under anyone who retunes points_per_level, or
     * who uses the command to grant points directly - both of which would leave players' spent totals
     * exceeding what they had ever earned.
     */
    private int pointsEarned;
    private int pointsSpent;

    private final Map<String, Integer> nodeRanks = new HashMap<>();

    public double getXp() {
        return xp;
    }

    public void setXp(double xp) {
        this.xp = Math.max(0.0, xp);
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = Math.max(1, level);
    }

    public int getPointsEarned() {
        return pointsEarned;
    }

    public void grantPoints(int points) {
        this.pointsEarned += points;
    }

    public int getPointsSpent() {
        return pointsSpent;
    }

    public int getPointsAvailable() {
        return Math.max(0, pointsEarned - pointsSpent);
    }

    public void spendPoints(int points) {
        this.pointsSpent += points;
    }

    public int getNodeRank(String nodeId) {
        return nodeRanks.getOrDefault(nodeId, 0);
    }

    public void setNodeRank(String nodeId, int rank) {
        if (rank <= 0) {
            nodeRanks.remove(nodeId);
        } else {
            nodeRanks.put(nodeId, rank);
        }
    }

    public Map<String, Integer> getNodeRanks() {
        return nodeRanks;
    }

    /** Wipes the tree but keeps the levels: what a respec would do, and what the reset command does. */
    public void clearNodes() {
        nodeRanks.clear();
        pointsSpent = 0;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("Xp", xp);
        tag.putInt("Level", level);
        tag.putInt("PointsEarned", pointsEarned);
        tag.putInt("PointsSpent", pointsSpent);

        CompoundTag nodes = new CompoundTag();
        nodeRanks.forEach(nodes::putInt);
        tag.put("Nodes", nodes);
        return tag;
    }

    public static SkillProgress load(CompoundTag tag) {
        SkillProgress progress = new SkillProgress();
        progress.xp = tag.getDouble("Xp");
        progress.level = Math.max(1, tag.getInt("Level"));
        progress.pointsEarned = tag.getInt("PointsEarned");
        progress.pointsSpent = tag.getInt("PointsSpent");

        CompoundTag nodes = tag.getCompound("Nodes");
        for (String nodeId : nodes.getAllKeys()) {
            int rank = nodes.getInt(nodeId);
            if (rank > 0) progress.nodeRanks.put(nodeId, rank);
        }
        return progress;
    }

    /** Whether this is worth writing out at all - saves a tag per untouched skill on every player. */
    public boolean isUntouched() {
        return xp == 0.0 && level == 1 && pointsEarned == 0 && pointsSpent == 0 && nodeRanks.isEmpty();
    }
}
