package net.finnigan.tommemod.client;

import net.finnigan.tommemod.TommeMod;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;

/**
 * Model layers for the mod's few non-GeckoLib entities - currently just the Warrior Villager, which
 * stays on vanilla's humanoid rig so it can animate like a player and wear real armor.
 */
public class ModModelLayers {

    public static final ModelLayerLocation WARRIOR_VILLAGER =
            new ModelLayerLocation(new ResourceLocation(TommeMod.MOD_ID, "warrior_villager"), "main");

    private ModModelLayers() {
    }
}
