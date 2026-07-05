package net.finnigan.tommemod.villager;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.block.ModBlocks;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.Set;

public class ModPoiTypes {
    public static final DeferredRegister<PoiType> POI_TYPES =
            DeferredRegister.create(ForgeRegistries.POI_TYPES, TommeMod.MOD_ID);

    public static final RegistryObject<PoiType> OVEN_POI = POI_TYPES.register("oven_poi", // Dedicate mod block
            () -> new PoiType(Set.copyOf(ModBlocks.OVEN.get().getStateDefinition().getPossibleStates()), 1, 1)); // How many villagers can claim job block
}