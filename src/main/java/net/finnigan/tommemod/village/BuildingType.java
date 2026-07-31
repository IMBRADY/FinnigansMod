package net.finnigan.tommemod.village;

import net.finnigan.tommemod.config.ModConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * A Builder Hub-buildable building type. Only HOUSE and WALLS are implemented right now (see
 * {@link #isImplemented()}) - the rest are recognized (and pre-configured, see ModConfig's
 * "builderHub" section) but the Builder Hub screen shows them as locked "Coming Soon" tiles.
 */
public enum BuildingType {
    HOUSE("House"),
    WALLS("Walls"),
    BANK("Bank"),
    OBSERVATORY("Observatory"),
    BARRACKS("Barracks");

    private final String displayName;

    BuildingType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isImplemented() {
        return this == HOUSE || this == WALLS;
    }

    public int getEmeraldCost() {
        return intAt(ModConfig.BUILDER_HUB_COST_EMERALDS.get(), 0);
    }

    public Item getResourceItem() {
        List<? extends String> items = ModConfig.BUILDER_HUB_RESOURCE_ITEM.get();
        if (ordinal() >= items.size()) return Items.AIR;
        ResourceLocation id = ResourceLocation.tryParse(items.get(ordinal()));
        Item item = id != null ? ForgeRegistries.ITEMS.getValue(id) : null;
        return item != null ? item : Items.AIR;
    }

    public int getResourceCount() {
        return intAt(ModConfig.BUILDER_HUB_RESOURCE_COUNT.get(), 0);
    }

    public int getRequiredBuilders() {
        return intAt(ModConfig.BUILDER_HUB_REQUIRED_BUILDERS.get(), 0);
    }

    private int intAt(List<? extends Integer> list, int fallback) {
        return ordinal() < list.size() ? list.get(ordinal()) : fallback;
    }
}
