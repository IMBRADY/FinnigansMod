package net.finnigan.tommemod.village;

import net.finnigan.tommemod.capability.reputation.ModReputationCapabilities;
import net.finnigan.tommemod.capability.reputation.ReputationTier;
import net.finnigan.tommemod.config.ModConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Everything that differs between one Chief Desk upgrade and the next, in one place: what it is
 * called, what it costs, what it takes to be allowed to buy it, what it does on purchase, and where
 * its level lives on the village.
 *
 * Gathered here because the same facts are needed in three places that must agree - the screen quotes
 * the price and the requirement, MonolithUpgradePacket charges and enforces them, and
 * MonolithBlockEntity ships the level back to the screen. Splitting them across those files is how a
 * displayed price and a charged one drift apart. Adding a fifth upgrade should mean adding a constant
 * here and nothing else.
 */
public enum VillageUpgrade {

    /**
     * First in the list on purpose: this is the upgrade the rest of a village's military hangs off,
     * since it is what its squires read to decide what they may arm a Warrior with. Unlike the others
     * it is gated on trust as well as goods - a village hands its armoury to a Chief who has earned it.
     */
    VILLAGE_TIER("Village Tier", Items.EMERALD, "emeralds") {
        @Override
        public int maxLevel() {
            return VillageTier.MAX;
        }

        @Override
        public int levelIn(VillageManager manager, UUID villageId) {
            return manager.getVillageTier(villageId);
        }

        @Override
        public void setLevelIn(VillageManager manager, UUID villageId, int level) {
            manager.setVillageTier(villageId, level);
        }

        @Override
        public String effectDescription(int level) {
            return VillageTier.describe(level);
        }

        @Override
        @Nullable
        public ReputationTier reputationRequiredFor(int nextLevel) {
            return VillageTier.reputationRequiredFor(nextLevel);
        }

        @Override
        protected List<? extends Integer> costTable() {
            return ModConfig.VILLAGE_TIER_UPGRADE_COST_EMERALDS.get();
        }
    },

    FARM_EFFICIENCY("Farm Efficiency", Items.HAY_BLOCK, "hay bales") {
        @Override
        public int maxLevel() {
            return ModConfig.FARM_EFFICIENCY_MAX_LEVEL.get();
        }

        @Override
        public int levelIn(VillageManager manager, UUID villageId) {
            return manager.getFarmEfficiencyLevel(villageId);
        }

        @Override
        public void setLevelIn(VillageManager manager, UUID villageId, int level) {
            manager.setFarmEfficiencyLevel(villageId, level);
        }

        @Override
        public String effectDescription(int level) {
            return "Crops grow " + (int) Math.round(level * ModConfig.FARM_EFFICIENCY_PERCENT_PER_LEVEL.get() * 100.0)
                    + "% faster";
        }

        @Override
        protected List<? extends Integer> costTable() {
            return ModConfig.FARM_EFFICIENCY_UPGRADE_COST_HAY_BALES.get();
        }
    },

    HEALTHY_WARRIORS("Healthy Warriors", Items.COOKED_BEEF, "cooked beef") {
        @Override
        public int maxLevel() {
            return ModConfig.HEALTHY_WARRIORS_MAX_LEVEL.get();
        }

        @Override
        public int levelIn(VillageManager manager, UUID villageId) {
            return manager.getHealthyWarriorsLevel(villageId);
        }

        @Override
        public void setLevelIn(VillageManager manager, UUID villageId, int level) {
            manager.setHealthyWarriorsLevel(villageId, level);
        }

        @Override
        public String effectDescription(int level) {
            return "Warriors have " + (int) Math.round(level * ModConfig.HEALTHY_WARRIORS_PERCENT_PER_LEVEL.get() * 100.0)
                    + "% more health";
        }

        @Override
        protected List<? extends Integer> costTable() {
            return ModConfig.HEALTHY_WARRIORS_UPGRADE_COST_COOKED_BEEF.get();
        }
    };

    private final String displayName;
    private final Item costItem;
    private final String costItemPlural;

    VillageUpgrade(String displayName, Item costItem, String costItemPlural) {
        this.displayName = displayName;
        this.costItem = costItem;
        this.costItemPlural = costItemPlural;
    }

    public abstract int maxLevel();

    public abstract int levelIn(VillageManager manager, UUID villageId);

    public abstract void setLevelIn(VillageManager manager, UUID villageId, int level);

    /** What this upgrade is currently doing for the village, phrased for the screen. */
    public abstract String effectDescription(int level);

    protected abstract List<? extends Integer> costTable();

    /**
     * Reputation the buyer must have with this village before {@code nextLevel} may be bought, or null
     * if goods alone are enough. Checked by both the screen (to explain itself) and the packet (to
     * enforce it) - a requirement only one of them knows about is a requirement players work around.
     */
    @Nullable
    public ReputationTier reputationRequiredFor(int nextLevel) {
        return null;
    }

    /**
     * Anything else standing between this player and the next level, phrased for them, or null if
     * nothing is. For upgrades that touch the world this is where "it cannot be built here" lives.
     */
    @Nullable
    public String purchaseBlocker(ServerLevel level, ServerPlayer player, VillageManager manager,
                                  UUID villageId, int currentLevel) {
        return null;
    }

    /** Run after the level has been recorded and paid for. Default: nothing beyond the level itself. */
    public void onPurchased(ServerLevel level, ServerPlayer player, VillageManager manager, UUID villageId) {
    }

    public String displayName() {
        return displayName;
    }

    public Item costItem() {
        return costItem;
    }

    public String costItemPlural() {
        return costItemPlural;
    }

    /**
     * What it costs to buy the level above {@code currentLevel}. Runs off the end of a short cost
     * table by repeating its last entry rather than failing, so shortening the table in config can
     * never leave an upgrade unbuyable.
     */
    public int costOfNextLevel(int currentLevel) {
        List<? extends Integer> costs = costTable();
        if (costs.isEmpty()) return 0;
        return costs.get(Math.min(Math.max(currentLevel, 0), costs.size() - 1));
    }

    /**
     * This player's reputation standing with this village. Lives here rather than at each call site
     * because both the screen and the packet need to ask it the same way.
     */
    public static ReputationTier standingOf(ServerPlayer player, UUID villageId) {
        return player.getCapability(ModReputationCapabilities.REPUTATION_HANDLER)
                .map(handler -> handler.getTier(villageId))
                .orElse(ReputationTier.NOVICE);
    }

    /** Decodes an upgrade sent over the wire, tolerating an id this build doesn't have. */
    public static VillageUpgrade byId(int id) {
        VillageUpgrade[] all = values();
        return id >= 0 && id < all.length ? all[id] : null;
    }
}
