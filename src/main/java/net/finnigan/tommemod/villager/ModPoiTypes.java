package net.finnigan.tommemod.villager;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.block.ModBlocks;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.Set;

public class ModPoiTypes {
    public static final DeferredRegister<PoiType> POI_TYPES =
            DeferredRegister.create(ForgeRegistries.POI_TYPES, TommeMod.MOD_ID);

    public static final RegistryObject<PoiType> OVEN_POI = POI_TYPES.register("oven_poi", // Dedicate mod block
            () -> new PoiType(Set.copyOf(ModBlocks.OVEN.get().getStateDefinition().getPossibleStates()), 1, 1)); // How many villagers can claim job block

    // Job site for the Elder Villager; never added to the vanilla acquirable_job_site tag, since
    // regular villagers must never auto-claim it as a profession - it's only ever assigned by our own
    // Elder-spawn trigger. Max 1 ticket enforces one Elder per claimed table.
    public static final RegistryObject<PoiType> ENCHANTING_TABLE_POI = POI_TYPES.register("enchanting_table_poi",
            () -> new PoiType(Set.copyOf(Blocks.ENCHANTING_TABLE.getStateDefinition().getPossibleStates()), 1, 1));
}