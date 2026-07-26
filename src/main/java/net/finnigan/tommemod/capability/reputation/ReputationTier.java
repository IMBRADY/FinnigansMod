package net.finnigan.tommemod.capability.reputation;

import net.finnigan.tommemod.config.ModConfig;

public enum ReputationTier {
    NOVICE, APPRENTICE, JOURNEYMAN, EXPERT, MASTER;

    public static ReputationTier fromScore(int score) {
        if (score >= ModConfig.TIER_MASTER_THRESHOLD.get()) return MASTER;
        if (score >= ModConfig.TIER_EXPERT_THRESHOLD.get()) return EXPERT;
        if (score >= ModConfig.TIER_JOURNEYMAN_THRESHOLD.get()) return JOURNEYMAN;
        if (score >= ModConfig.TIER_APPRENTICE_THRESHOLD.get()) return APPRENTICE;
        return NOVICE;
    }

    public boolean isAtLeast(ReputationTier other) {
        return this.ordinal() >= other.ordinal();
    }
}
