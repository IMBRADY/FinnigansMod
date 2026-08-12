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
    public static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> FARM_EFFICIENCY_UPGRADE_COST_EMERALDS;

    // builder hub
    // The four BUILDER_HUB_* lists below are indexed by BuildingType's enum ordinal
    // (HOUSE, WALLS, BANK, OBSERVATORY, BARRACKS) - one entry per building type, in that order.
    public static final ForgeConfigSpec.IntValue BUILDER_HUB_TICK_INTERVAL_TICKS;
    public static final ForgeConfigSpec.IntValue BUILDER_HUB_MAX_FOOTPRINT_VARIANCE;
    public static final ForgeConfigSpec.IntValue BUILDER_HUB_REGION_PADDING_BLOCKS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> BUILDER_HUB_COST_EMERALDS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> BUILDER_HUB_RESOURCE_ITEM;
    public static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> BUILDER_HUB_RESOURCE_COUNT;
    public static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> BUILDER_HUB_REQUIRED_BUILDERS;

    // quality of life
    public static final ForgeConfigSpec.BooleanValue SWING_THROUGH_PLANTS;
    public static final ForgeConfigSpec.BooleanValue PET_FRIENDLY_FIRE_PROTECTION;
    public static final ForgeConfigSpec.BooleanValue POISONOUS_POTATO_AGE_LOCK;
    public static final ForgeConfigSpec.BooleanValue FIRE_ENCHANT_PARTICLES;

    // unhoisted titan
    public static final ForgeConfigSpec.DoubleValue TITAN_BLAST_RADIUS_BLOCKS;
    public static final ForgeConfigSpec.DoubleValue TITAN_ARMOR_SCAN_RADIUS_BLOCKS;
    public static final ForgeConfigSpec.DoubleValue TITAN_SWIM_SPEED_BONUS;

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
        FARM_EFFICIENCY_UPGRADE_COST_EMERALDS = builder.comment("Emerald cost to reach each Farm Efficiency level (index 0 = cost of level 1, etc.)")
                .defineList("farmEfficiencyUpgradeCostEmeralds", List.of(32, 64, 128), obj -> obj instanceof Integer i && i >= 0);
        builder.pop();

        builder.push("builderHub");
        BUILDER_HUB_TICK_INTERVAL_TICKS = builder.comment("Ticks between each block placed during construction (20 = one block per second)")
                .defineInRange("builderHubTickIntervalTicks", 20, 1, Integer.MAX_VALUE);
        BUILDER_HUB_MAX_FOOTPRINT_VARIANCE = builder.comment("Max allowed height difference (blocks) across a building's footprint; placement is refused above this")
                .defineInRange("builderHubMaxFootprintVariance", 3, 0, Integer.MAX_VALUE);
        BUILDER_HUB_REGION_PADDING_BLOCKS = builder.comment("Extra padding (blocks) added to a village's bounding region for where a construction banner may be placed")
                .defineInRange("builderHubRegionPaddingBlocks", 16, 0, Integer.MAX_VALUE);
        builder.comment("The four lists below are indexed by BuildingType's ordinal (HOUSE, WALLS, BANK, OBSERVATORY, BARRACKS) - one entry per building type, in that order.",
                "Bank/Observatory/Barracks aren't buildable yet regardless of these values (see BuildingType.implemented) - they're pre-configured for when they are.");
        BUILDER_HUB_COST_EMERALDS = builder.comment("Emerald cost per building type")
                .defineList("builderHubCostEmeralds", List.of(16, 8, 64, 96, 48), obj -> obj instanceof Integer i && i >= 0);
        BUILDER_HUB_RESOURCE_ITEM = builder.comment("Resource item (registry id) required per building type")
                .defineList("builderHubResourceItem", List.of(
                        "minecraft:oak_planks", "minecraft:cobblestone", "minecraft:iron_block", "minecraft:glass", "minecraft:cobblestone"
                ), obj -> obj instanceof String);
        BUILDER_HUB_RESOURCE_COUNT = builder.comment("Resource item count required per building type")
                .defineList("builderHubResourceCount", List.of(32, 48, 8, 24, 64), obj -> obj instanceof Integer i && i >= 0);
        BUILDER_HUB_REQUIRED_BUILDERS = builder.comment("Minimum Builder Villagers the village must have to unlock each building type")
                .defineList("builderHubRequiredBuilders", List.of(0, 1, 2, 3, 2), obj -> obj instanceof Integer i && i >= 0);
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
