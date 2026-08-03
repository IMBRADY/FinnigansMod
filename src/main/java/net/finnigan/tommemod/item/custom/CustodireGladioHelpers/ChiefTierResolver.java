package net.finnigan.tommemod.item.custom.CustodireGladioHelpers;

import net.finnigan.tommemod.capability.reputation.ModReputationCapabilities;
import net.finnigan.tommemod.capability.reputation.ReputationTier;
import net.finnigan.tommemod.village.VillageManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

import java.util.Set;
import java.util.UUID;

/**
 * Resolves the reputation tier Custodire Gladio scales off: the player's standing in whichever village
 * they are Chief of. A player can hold more than one Chief seat, so the best of them is what counts -
 * the weapon rewards their strongest chiefdom rather than punishing them for holding a weak one.
 * Deliberately not tied to where the player currently is: unlike the Chief attribute buff
 * (ChiefBuffTickHandler), this is a property of the wielder, not of standing inside their own village.
 */
public class ChiefTierResolver {

    /** Damage/health/shield scaling granted per tier above Novice. */
    public static final double BONUS_PER_TIER = 0.20;

    private ChiefTierResolver() {
    }

    public static ReputationTier bestChiefTier(Player player) {
        if (!(player.level() instanceof ServerLevel level)) return ReputationTier.NOVICE;

        Set<UUID> chiefdoms = VillageManager.get(level).getVillagesChiefedBy(player.getUUID());
        if (chiefdoms.isEmpty()) return ReputationTier.NOVICE;

        return player.getCapability(ModReputationCapabilities.REPUTATION_HANDLER).map(handler -> {
            ReputationTier best = ReputationTier.NOVICE;
            for (UUID villageId : chiefdoms) {
                ReputationTier tier = handler.getTier(villageId);
                if (tier.isAtLeast(best)) best = tier;
            }
            return best;
        }).orElse(ReputationTier.NOVICE);
    }

    /** 1.0 at Novice, +20% per tier above it, so 1.8 at Master. */
    public static double scaleFor(Player player) {
        return 1.0 + BONUS_PER_TIER * bestChiefTier(player).ordinal();
    }
}
