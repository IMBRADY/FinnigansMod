package net.finnigan.tommemod.config;

import net.finnigan.tommemod.capability.reputation.ReputationTier;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

// NOTE!!!
// If you already clean built your minecraft world, and you changed these values later,
// YOU MUST change them in tommemod-common.toml in ..\run\config (find with Ctrl+N)

public class ModConfig {

    public static final ForgeConfigSpec COMMON_SPEC;

    // reputation
    public static final ForgeConfigSpec.IntValue TIER_APPRENTICE_THRESHOLD;
    public static final ForgeConfigSpec.IntValue TIER_JOURNEYMAN_THRESHOLD;
    public static final ForgeConfigSpec.IntValue TIER_EXPERT_THRESHOLD;
    public static final ForgeConfigSpec.IntValue TIER_MASTER_THRESHOLD;
    public static final ForgeConfigSpec.IntValue REPUTATION_FLOOR;

    public static final ForgeConfigSpec.IntValue TRADE_GAIN;
    public static final ForgeConfigSpec.IntValue VILLAGER_HURT_LOSS;
    public static final ForgeConfigSpec.IntValue VILLAGER_KILLED_LOSS;
    public static final ForgeConfigSpec.IntValue IRON_GOLEM_KILLED_LOSS;
    public static final ForgeConfigSpec.IntValue HOSTILE_MOB_KILLED_IN_VILLAGE_GAIN;

    // village (POI clustering)
    public static final ForgeConfigSpec.IntValue POI_LINK_RADIUS;
    public static final ForgeConfigSpec.IntValue MAX_BFS_POIS;
    public static final ForgeConfigSpec.IntValue MAX_BFS_ITERATIONS;
    public static final ForgeConfigSpec.IntValue MAX_VILLAGE_RADIUS_BLOCKS;
    public static final ForgeConfigSpec.IntValue RESOLUTION_CACHE_TTL_TICKS;

    // elder
    public static final ForgeConfigSpec.IntValue MIN_NEARBY_VILLAGERS;
    public static final ForgeConfigSpec.IntValue ELDER_MAX_WANDER_BLOCKS;
    public static final ForgeConfigSpec.IntValue ELDER_TETHER_CHECK_INTERVAL_TICKS;

    // warrior
    public static final ForgeConfigSpec.IntValue MAX_WARRIORS_NOVICE;
    public static final ForgeConfigSpec.IntValue MAX_WARRIORS_APPRENTICE;
    public static final ForgeConfigSpec.IntValue MAX_WARRIORS_JOURNEYMAN;
    public static final ForgeConfigSpec.IntValue MAX_WARRIORS_EXPERT;
    public static final ForgeConfigSpec.IntValue MAX_WARRIORS_MASTER;

    // chief
    public enum DiscountType { PERCENTAGE, FLAT }
    public static final ForgeConfigSpec.EnumValue<DiscountType> CHIEF_DISCOUNT_TYPE;
    public static final ForgeConfigSpec.DoubleValue CHIEF_DISCOUNT_PERCENT;
    public static final ForgeConfigSpec.IntValue CHIEF_DISCOUNT_FLAT_EMERALDS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> CHIEF_BUFF_ATTRIBUTE;
    public static final ForgeConfigSpec.EnumValue<net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation> CHIEF_BUFF_OPERATION;
    public static final ForgeConfigSpec.DoubleValue CHIEF_BUFF_AMOUNT_PER_VILLAGER;
    public static final ForgeConfigSpec.DoubleValue CHIEF_BUFF_CAP;
    public static final ForgeConfigSpec.IntValue CHIEF_BUFF_TICK_INTERVAL_TICKS;
    public static final ForgeConfigSpec.IntValue CHIEF_BUFF_REGION_PADDING_BLOCKS;

    // monolith
    public static final ForgeConfigSpec.IntValue MONOLITH_REFRESH_INTERVAL_TICKS;
    public static final ForgeConfigSpec.IntValue MONOLITH_MINIMAP_RADIUS_BLOCKS;
    public static final ForgeConfigSpec.DoubleValue FARM_EFFICIENCY_PERCENT_PER_LEVEL;
    public static final ForgeConfigSpec.IntValue FARM_EFFICIENCY_MAX_LEVEL;
    public static final ForgeConfigSpec.IntValue FARM_EFFICIENCY_TICK_INTERVAL_TICKS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> FARM_EFFICIENCY_UPGRADE_COST_HAY_BALES;
    public static final ForgeConfigSpec.DoubleValue HEALTHY_WARRIORS_PERCENT_PER_LEVEL;
    public static final ForgeConfigSpec.IntValue HEALTHY_WARRIORS_MAX_LEVEL;
    public static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> HEALTHY_WARRIORS_UPGRADE_COST_COOKED_BEEF;
    public static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> VILLAGE_TIER_UPGRADE_COST_EMERALDS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> VILLAGE_TIER_MAX_RANGE_BLOCKS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> VILLAGE_TIER_BUFF_ATTRIBUTES;
    public static final ForgeConfigSpec.DoubleValue VILLAGE_TIER_BUFF_AMOUNT;
    public static final ForgeConfigSpec.IntValue VILLAGE_TIER_BUFF_MIN_TIER;
    public static final ForgeConfigSpec.IntValue VILLAGE_WALLS_SPAWN_MARGIN_BLOCKS;

    // squires
    public static final ForgeConfigSpec.IntValue SQUIRE_TICK_INTERVAL_TICKS;
    public static final ForgeConfigSpec.IntValue SQUIRE_RANGE_BLOCKS;
    public static final ForgeConfigSpec.DoubleValue SQUIRE_DIAMOND_ROLL_CHANCE;

    // builder villagers + blueprint mode (section keeps its old "builderHub" name so existing configs keep their values)
    // Builder villagers + blueprint mode. What each building costs and needs lives in its own file
    // under data/tommemod/blueprints, not here.
    public static final ForgeConfigSpec.IntValue BUILDER_TICKS_PER_BLOCK;
    public static final ForgeConfigSpec.IntValue BUILDER_SEARCH_RADIUS_BLOCKS;
    public static final ForgeConfigSpec.IntValue BLUEPRINT_MAX_GROUND_GAP;
    public static final ForgeConfigSpec.IntValue BUILDER_HUB_REGION_PADDING_BLOCKS;
    public static final ForgeConfigSpec.IntValue BLUEPRINT_MAX_ACTIVE_SITES;
    public static final ForgeConfigSpec.IntValue BLUEPRINT_FLIGHT_MARGIN_BLOCKS;
    public static final ForgeConfigSpec.IntValue BLUEPRINT_MAX_WALL_SITES;
    public static final ForgeConfigSpec.IntValue WALL_MAX_GROUND_GAP;

    // what finished buildings do for their village
    public static final ForgeConfigSpec.IntValue WARRIOR_SLEEP_TICKS;
    public static final ForgeConfigSpec.IntValue WARRIOR_RESPAWN_TICKS;
    public static final ForgeConfigSpec.IntValue WARRIOR_SLEEP_HEAL_INTERVAL_TICKS;
    public static final ForgeConfigSpec.IntValue OBSERVATORY_MAP_BONUS_BLOCKS;
    public static final ForgeConfigSpec.IntValue FARM_REPLANTS_PER_EMERALD;
    public static final ForgeConfigSpec.IntValue FARMER_WORK_RADIUS_BLOCKS;

    // quality of life
    public static final ForgeConfigSpec.BooleanValue SWING_THROUGH_PLANTS;
    public static final ForgeConfigSpec.BooleanValue PET_FRIENDLY_FIRE_PROTECTION;
    public static final ForgeConfigSpec.BooleanValue POISONOUS_POTATO_AGE_LOCK;
    public static final ForgeConfigSpec.BooleanValue FIRE_ENCHANT_PARTICLES;

    // unhoisted titan
    public static final ForgeConfigSpec.DoubleValue TITAN_BLAST_RADIUS_BLOCKS;
    public static final ForgeConfigSpec.DoubleValue TITAN_ARMOR_SCAN_RADIUS_BLOCKS;
    public static final ForgeConfigSpec.DoubleValue TITAN_SWIM_SPEED_BONUS;

    // ---- Skills ----
    public static final ForgeConfigSpec.DoubleValue SKILL_XP_MULTIPLIER;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("reputation");
        builder.comment("Every player starts as Novice (0) in every village. Tiers are always derived live from the",
                "current reputation score, so losing reputation can demote a player back down a tier.");
        TIER_APPRENTICE_THRESHOLD = builder.comment("Reputation needed to become Apprentice")
                .defineInRange("tierApprenticeThreshold", 200, Integer.MIN_VALUE, Integer.MAX_VALUE);
        TIER_JOURNEYMAN_THRESHOLD = builder.comment("Reputation needed to become Journeyman")
                .defineInRange("tierJourneymanThreshold", 500, Integer.MIN_VALUE, Integer.MAX_VALUE);
        TIER_EXPERT_THRESHOLD = builder.comment("Reputation needed to become Expert")
                .defineInRange("tierExpertThreshold", 1000, Integer.MIN_VALUE, Integer.MAX_VALUE);
        TIER_MASTER_THRESHOLD = builder.comment("Reputation needed to become Master")
                .defineInRange("tierMasterThreshold", 2500, Integer.MIN_VALUE, Integer.MAX_VALUE);
        REPUTATION_FLOOR = builder.comment("Reputation with a village is never allowed to drop below this value")
                .defineInRange("reputationFloor", 0, Integer.MIN_VALUE, Integer.MAX_VALUE);

        builder.comment("Reputation gained/lost per activity, mirroring vanilla villager gossip categories.");
        TRADE_GAIN = builder.comment("Small gain per completed villager trade")
                .defineInRange("tradeGain", 1, Integer.MIN_VALUE, Integer.MAX_VALUE);
        VILLAGER_HURT_LOSS = builder.comment("Loss for non-lethally hurting a villager")
                .defineInRange("villagerHurtLoss", -15, Integer.MIN_VALUE, Integer.MAX_VALUE);
        VILLAGER_KILLED_LOSS = builder.comment("Big loss for killing a villager")
                .defineInRange("villagerKilledLoss", -50, Integer.MIN_VALUE, Integer.MAX_VALUE);
        IRON_GOLEM_KILLED_LOSS = builder.comment("Loss for killing an Iron Golem (a village's defender)")
                .defineInRange("ironGolemKilledLoss", -15, Integer.MIN_VALUE, Integer.MAX_VALUE);
        HOSTILE_MOB_KILLED_IN_VILLAGE_GAIN = builder.comment("Small gain for killing a hostile mob while inside a village")
                .defineInRange("hostileMobKilledInVillageGain", 4, Integer.MIN_VALUE, Integer.MAX_VALUE);
        builder.pop();

        builder.push("village");
        builder.comment("Villages are defined by clustering claimed POIs (beds, job sites, bells): each POI projects",
                "a radius, and overlapping radii (within 2x that radius of each other) merge into one village.");
        POI_LINK_RADIUS = builder.comment("Radius (blocks) each claimed POI projects; two POIs within 2x this distance link into the same village")
                .defineInRange("poiLinkRadius", 32, 1, 256);
        MAX_BFS_POIS = builder.comment("Safety cap: max POIs visited while resolving one village cluster")
                .defineInRange("maxBfsPois", 4096, 16, Integer.MAX_VALUE);
        MAX_BFS_ITERATIONS = builder.comment("Safety cap: max BFS loop iterations while resolving one village cluster")
                .defineInRange("maxBfsIterations", 8192, 16, Integer.MAX_VALUE);
        MAX_VILLAGE_RADIUS_BLOCKS = builder.comment("Safety cap: BFS will not expand a village past this distance from its anchor")
                .defineInRange("maxVillageRadiusBlocks", 512, 32, Integer.MAX_VALUE);
        RESOLUTION_CACHE_TTL_TICKS = builder.comment("How long (ticks) a resolved village lookup is cached in memory, since combat/trade events call it often")
                .defineInRange("resolutionCacheTtlTicks", 100, 0, Integer.MAX_VALUE);
        builder.pop();

        builder.push("elder");
        MIN_NEARBY_VILLAGERS = builder.comment("Minimum Villager population required in a village before a placed Monolith becomes claimable as the Elder Villager's job site")
                .defineInRange("minNearbyVillagers", 3, 0, Integer.MAX_VALUE);
        ELDER_MAX_WANDER_BLOCKS = builder.comment("Max distance (blocks) an Elder Villager may wander from its village's anchor before being teleported back")
                .defineInRange("elderMaxWanderBlocks", 50, 1, Integer.MAX_VALUE);
        ELDER_TETHER_CHECK_INTERVAL_TICKS = builder.comment("How often (ticks) an Elder Villager's distance from its village is checked")
                .defineInRange("elderTetherCheckIntervalTicks", 40, 1, Integer.MAX_VALUE);
        builder.pop();

        builder.push("warrior");
        builder.comment("Maximum Warrior Villagers a single village may have, scaled by the converting player's own",
                "reputation tier with that village - a more trusted player can arm more Warriors.");
        MAX_WARRIORS_NOVICE = builder.comment("Max Warriors when the converting player is Novice in this village")
                .defineInRange("maxWarriorsNovice", 1, 0, Integer.MAX_VALUE);
        MAX_WARRIORS_APPRENTICE = builder.comment("Max Warriors when the converting player is Apprentice in this village")
                .defineInRange("maxWarriorsApprentice", 4, 0, Integer.MAX_VALUE);
        MAX_WARRIORS_JOURNEYMAN = builder.comment("Max Warriors when the converting player is Journeyman in this village")
                .defineInRange("maxWarriorsJourneyman", 10, 0, Integer.MAX_VALUE);
        MAX_WARRIORS_EXPERT = builder.comment("Max Warriors when the converting player is Expert in this village")
                .defineInRange("maxWarriorsExpert", 20, 0, Integer.MAX_VALUE);
        MAX_WARRIORS_MASTER = builder.comment("Max Warriors when the converting player is Master in this village")
                .defineInRange("maxWarriorsMaster", 40, 0, Integer.MAX_VALUE);
        builder.pop();

        builder.push("chief");
        CHIEF_DISCOUNT_TYPE = builder.comment("Whether the Village Chief trade discount is a percentage of each offer's price, or a flat emerald amount off")
                .defineEnum("discountType", DiscountType.PERCENTAGE);
        CHIEF_DISCOUNT_PERCENT = builder.comment("Discount fraction applied to trade prices for the Village Chief (used when discountType = PERCENTAGE)")
                .defineInRange("discountPercent", 0.15, 0.0, 1.0);
        CHIEF_DISCOUNT_FLAT_EMERALDS = builder.comment("Flat emerald discount applied to trade prices for the Village Chief (used when discountType = FLAT)")
                .defineInRange("discountFlatEmeralds", 1, 0, Integer.MAX_VALUE);
        CHIEF_BUFF_ATTRIBUTE = builder.comment("Attribute (registry id) buffed for the Village Chief while inside their village")
                .defineListAllowEmpty(
                        "buffAttributes",
                        List.of("minecraft:generic.movement_speed", "minecraft:generic.armor_toughness", "minecraft:generic.knockback_resistance"),
                        obj -> obj instanceof String
                );
        CHIEF_BUFF_OPERATION = builder.comment("All values are multiplied to total not added :)))))))")
                .defineEnum("buffAttributeOperation", net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL);
        CHIEF_BUFF_AMOUNT_PER_VILLAGER = builder.comment("Buff amount granted per Villager currently in the Chief's village")
                .defineInRange("buffAmountPerVillager", 0.003, 0.0, Double.MAX_VALUE);
        CHIEF_BUFF_CAP = builder.comment("Maximum total buff amount regardless of village population")
                .defineInRange("buffCap", 0.15, 0.0, Double.MAX_VALUE);
        CHIEF_BUFF_TICK_INTERVAL_TICKS = builder.comment("How often (ticks) the Chief buff is recalculated")
                .defineInRange("buffTickIntervalTicks", 30, 1, Integer.MAX_VALUE);
        CHIEF_BUFF_REGION_PADDING_BLOCKS = builder.comment("Extra padding (blocks) added to a village's bounding region for buff/population purposes, so the buff doesn't flicker at the boundary")
                .defineInRange("buffRegionPaddingBlocks", 8, 0, Integer.MAX_VALUE);
        builder.pop();

        builder.push("monolith");
        MONOLITH_REFRESH_INTERVAL_TICKS = builder.comment("How often (ticks) an open Monolith screen's live data (population/defense counts, minimap markers) is rescanned and pushed to viewers")
                .defineInRange("monolithRefreshIntervalTicks", 5, 1, Integer.MAX_VALUE);
        MONOLITH_MINIMAP_RADIUS_BLOCKS = builder.comment("Radius (blocks) around the Monolith that the tactical minimap tab scans for terrain/markers")
                .defineInRange("monolithMinimapRadiusBlocks", 64, 16, 256);
        FARM_EFFICIENCY_PERCENT_PER_LEVEL = builder.comment("Extra crop growth chance granted per Farm Efficiency level")
                .defineInRange("farmEfficiencyPercentPerLevel", 0.10, 0.0, 1.0);
        FARM_EFFICIENCY_MAX_LEVEL = builder.comment("Maximum Farm Efficiency level a village can be upgraded to")
                .defineInRange("farmEfficiencyMaxLevel", 3, 0, Integer.MAX_VALUE);
        FARM_EFFICIENCY_TICK_INTERVAL_TICKS = builder.comment("How often (ticks) Farm Efficiency's crop-growth boost is rolled per nearby village")
                .defineInRange("farmEfficiencyTickIntervalTicks", 20, 1, Integer.MAX_VALUE);
        FARM_EFFICIENCY_UPGRADE_COST_HAY_BALES = builder.comment("Hay bale cost to reach each Farm Efficiency level (index 0 = cost of level 1, etc.)")
                .defineList("farmEfficiencyUpgradeCostHayBales", List.of(32, 64, 128), obj -> obj instanceof Integer i && i >= 0);
        HEALTHY_WARRIORS_PERCENT_PER_LEVEL = builder.comment("Extra Warrior Villager max health granted per Healthy Warriors level")
                .defineInRange("healthyWarriorsPercentPerLevel", 0.15, 0.0, 10.0);
        HEALTHY_WARRIORS_MAX_LEVEL = builder.comment("Maximum Healthy Warriors level a village can be upgraded to")
                .defineInRange("healthyWarriorsMaxLevel", 5, 0, Integer.MAX_VALUE);
        HEALTHY_WARRIORS_UPGRADE_COST_COOKED_BEEF = builder.comment("Cooked beef cost to reach each Healthy Warriors level (index 0 = cost of level 1, etc.)")
                .defineList("healthyWarriorsUpgradeCostCookedBeef", List.of(20, 40, 60, 80, 100),
                        obj -> obj instanceof Integer i && i >= 0);

        builder.comment("Village Tier is the upgrade the rest of a village's military hangs off: it is what decides",
                "what the Armorer, Weaponsmith, Fletcher and Cleric squires are allowed to hand a Warrior.",
                "Buying a level also needs the Chief's own reputation with the village to be high enough -",
                "Journeyman for tier 1, Expert for tier 2, Master for tier 3 (see VillageUpgrade).");
        VILLAGE_TIER_UPGRADE_COST_EMERALDS = builder.comment("Emerald cost to reach each Village Tier (index 0 = cost of tier 1, etc.)")
                .defineList("villageTierUpgradeCostEmeralds", List.of(32, 96, 256), obj -> obj instanceof Integer i && i >= 0);
        VILLAGE_TIER_MAX_RANGE_BLOCKS = builder.comment("PLACEHOLDER VALUES. Cap (blocks) on how far a village's region may reach, indexed by tier",
                        "(index 0 = tier 0). Chosen well above the default poiLinkRadius so no existing village shrinks",
                        "on upgrading - pick the real numbers here once they are decided.")
                .defineList("villageTierMaxRangeBlocks", List.of(96, 128, 160, 192), obj -> obj instanceof Integer i && i > 0);
        VILLAGE_TIER_BUFF_ATTRIBUTES = builder.comment("Attributes (registry ids) buffed for a village's own players while they stand inside it, once the village is tier 2+")
                .defineListAllowEmpty(
                        "villageTierBuffAttributes",
                        List.of("minecraft:generic.movement_speed", "minecraft:generic.armor"),
                        obj -> obj instanceof String
                );
        VILLAGE_TIER_BUFF_AMOUNT = builder.comment("Size of that buff, as a MULTIPLY_TOTAL fraction. Stacks with the separate Chief buff rather than replacing it")
                .defineInRange("villageTierBuffAmount", 0.05, 0.0, 10.0);
        VILLAGE_TIER_BUFF_MIN_TIER = builder.comment("Village Tier at which that buff starts applying")
                .defineInRange("villageTierBuffMinTier", 2, 0, Integer.MAX_VALUE);

        builder.comment("Village walls are built in blueprint mode from Stone Walls, wall corners and Gatehouses. Once",
                "they close a loop, the ring (and everything inside it) is what counts as the village, and every",
                "wave spawn is pushed outside it.");
        VILLAGE_WALLS_SPAWN_MARGIN_BLOCKS = builder.comment("How far (blocks) beyond the wall a raid wave - or any future wave type - is pushed before it spawns")
                .defineInRange("villageWallsSpawnMarginBlocks", 12, 1, 128);
        builder.pop();

        builder.push("squire");
        builder.comment("Armorers, Weaponsmiths, Fletchers and Clerics equip their village's Warriors during working",
                "hours. Each may hand out one piece per Minecraft day; the Cleric is the exception, throwing",
                "potions as often as it has a Warrior that wants one.");
        SQUIRE_TICK_INTERVAL_TICKS = builder.comment("How often (ticks) squires are given a chance to act. Doubles as the Cleric's throw cooldown")
                .defineInRange("squireTickIntervalTicks", 100, 1, Integer.MAX_VALUE);
        SQUIRE_RANGE_BLOCKS = builder.comment("How close (blocks) a Warrior must be for a squire to equip or heal it")
                .defineInRange("squireRangeBlocks", 16, 1, 128);
        SQUIRE_DIAMOND_ROLL_CHANCE = builder.comment("Chance a tier 3 Weaponsmith's work period rolls a diamond weapon instead of an iron one")
                .defineInRange("squireDiamondRollChance", 0.10, 0.0, 1.0);
        builder.pop();

        builder.push("builderHub");
        BUILDER_TICKS_PER_BLOCK = builder.comment("Ticks each Builder Villager spends per block it places (20 = one block per second per builder). Clearing terrain takes half as long")
                .defineInRange("builderTicksPerBlock", 4, 1, 200);
        BUILDER_SEARCH_RADIUS_BLOCKS = builder.comment("How far (blocks, horizontally) from a construction site a Builder Villager will be called in from")
                .defineInRange("builderSearchRadiusBlocks", 96, 8, 512);
        BLUEPRINT_MAX_GROUND_GAP = builder.comment("Deepest drop (blocks) under a building's floor that builders will fill with foundation; deeper and placement is refused")
                .defineInRange("blueprintMaxGroundGap", 6, 0, 64);
        BUILDER_HUB_REGION_PADDING_BLOCKS = builder.comment("Extra padding (blocks) beyond the village's region where a blueprint may still be placed")
                .defineInRange("builderHubRegionPaddingBlocks", 16, 0, Integer.MAX_VALUE);
        BLUEPRINT_MAX_ACTIVE_SITES = builder.comment("Most constructions one village may have under way at once")
                .defineInRange("blueprintMaxActiveSites", 4, 1, 64);
        BLUEPRINT_FLIGHT_MARGIN_BLOCKS = builder.comment("How far (blocks) past the village's region the blueprint camera may fly")
                .defineInRange("blueprintFlightMarginBlocks", 48, 8, 512);
        BLUEPRINT_MAX_WALL_SITES = builder.comment("Most wall pieces (walls, corners, gatehouses) one village may have under way at once. Counted separately from blueprintMaxActiveSites, so a long wall doesn't hold up everything else")
                .defineInRange("blueprintMaxWallSites", 48, 1, 512);
        WALL_MAX_GROUND_GAP = builder.comment("Deepest drop (blocks) under a wall piece that builders will fill with foundation, so walls never float over a valley")
                .defineInRange("wallMaxGroundGap", 24, 0, 128);
        builder.pop();

        builder.push("buildings");
        WARRIOR_SLEEP_TICKS = builder.comment("How long each Barracks Warrior sleeps per day, in ticks (an ordinary villager sleeps about 12000). Shifts are staggered so the rest stay on guard")
                .defineInRange("warriorSleepTicks", 12000, 0, 24000);
        WARRIOR_RESPAWN_TICKS = builder.comment("How long after a Barracks Warrior dies before a replacement turns up at its bunk (24000 = one Minecraft day)")
                .defineInRange("warriorRespawnTicks", 24000, 20, Integer.MAX_VALUE);
        WARRIOR_SLEEP_HEAL_INTERVAL_TICKS = builder.comment("A sleeping Warrior heals one health point every this many ticks")
                .defineInRange("warriorSleepHealIntervalTicks", 40, 1, 72000);
        OBSERVATORY_MAP_BONUS_BLOCKS = builder.comment("How many blocks each standing Observatory adds to the Chief Desk map's radius")
                .defineInRange("observatoryMapBonusBlocks", 64, 0, 512);
        FARM_REPLANTS_PER_EMERALD = builder.comment("While the village has a Bank, every this many crops a Farmer replants on a Farm Plot adds one emerald to the village's wealth")
                .defineInRange("farmReplantsPerEmerald", 4, 1, 1000);
        FARMER_WORK_RADIUS_BLOCKS = builder.comment("How far (blocks) from its composter a Farmer looks for ripe crops to harvest and replant. Farm Plots in the village are always worked")
                .defineInRange("farmerWorkRadiusBlocks", 24, 4, 96);
        builder.pop();

        builder.push("qualityOfLife");
        builder.comment("Small standalone behaviour tweaks. Each is a separate toggle because these are",
                "the features most likely to overlap with another mod doing the same thing - turn one off",
                "rather than uninstalling the other mod.");
        SWING_THROUGH_PLANTS = builder.comment("Left-clicking through grass/flowers hits the mob behind them instead of breaking the plant.",
                        "Turn this off if you also run Swing Through Grass or similar, or attacks may register twice.")
                .define("swingThroughPlants", true);
        PET_FRIENDLY_FIRE_PROTECTION = builder.comment("You cannot damage your own tamed pets unless they are sitting")
                .define("petFriendlyFireProtection", true);
        POISONOUS_POTATO_AGE_LOCK = builder.comment("Feeding a baby animal a poisonous potato freezes it as a baby; feeding it again releases it")
                .define("poisonousPotatoAgeLock", true);
        FIRE_ENCHANT_PARTICLES = builder.comment("Purely cosmetic flame particles trail items enchanted with Fire Aspect or Flame")
                .define("fireEnchantParticles", true);
        builder.pop();

        builder.push("unhoistedTitan");
        builder.comment("The two radii below are deliberately separate knobs: one governs the crouch+right-click blast,",
                "the other the passive's enemy headcount for bonus armor - they are not meant to track each other.");
        TITAN_BLAST_RADIUS_BLOCKS = builder.comment("Radius (blocks) of the crouch+right-click water blast")
                .defineInRange("blastRadiusBlocks", 5.0, 0.5, 64.0);
        TITAN_ARMOR_SCAN_RADIUS_BLOCKS = builder.comment("Radius (blocks) scanned for nearby enemies when computing the passive's bonus armor (1 armor point per 5 enemies, capped at 10)")
                .defineInRange("armorScanRadiusBlocks", 12.0, 0.5, 64.0);
        TITAN_SWIM_SPEED_BONUS = builder.comment("Permanent swim speed bonus while held, as a fraction of the base swim speed (0.5 = +50%)")
                .defineInRange("swimSpeedBonus", 0.5, 0.0, 10.0);
        builder.pop();

        builder.push("skills");
        builder.comment("Per-skill rates, curves and trees live in the datapack files under",
                "data/tommemod/skill_trees/ - this is only the global dial over all of them.");
        SKILL_XP_MULTIPLIER = builder.comment("Multiplier applied to every skill experience award (2.0 halves the grind)")
                .defineInRange("xpMultiplier", 1.0, 0.0, 100.0);
        builder.pop();

        COMMON_SPEC = builder.build();
    }

    public static int maxWarriorsForTier(ReputationTier tier) {
        return switch (tier) {
            case NOVICE -> MAX_WARRIORS_NOVICE.get();
            case APPRENTICE -> MAX_WARRIORS_APPRENTICE.get();
            case JOURNEYMAN -> MAX_WARRIORS_JOURNEYMAN.get();
            case EXPERT -> MAX_WARRIORS_EXPERT.get();
            case MASTER -> MAX_WARRIORS_MASTER.get();
        };
    }

    private ModConfig() {
    }
}
