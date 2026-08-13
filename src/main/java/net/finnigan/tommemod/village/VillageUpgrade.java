package net.finnigan.tommemod.village;

import net.finnigan.tommemod.config.ModConfig;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.UUID;

/**
 * Everything that differs between one Chief Desk upgrade and the next, in one place: what it is
 * called, what it costs, how far it goes, and where its level lives on the village.
 *
 * Gathered here because the same three facts are needed in three places that must agree - the screen
 * quotes the price, MonolithUpgradePacket charges it, and MonolithBlockEntity ships the level back to
 * the screen. Splitting them across those files is how a displayed price and a charged one drift
 * apart. Adding a fourth upgrade should mean adding a constant here and nothing else.
 */
public enum VillageUpgrade {

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

    /** Decodes an upgrade sent over the wire, tolerating an id this build doesn't have. */
    public static VillageUpgrade byId(int id) {
        VillageUpgrade[] all = values();
        return id >= 0 && id < all.length ? all[id] : null;
    }
}
