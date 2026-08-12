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

    // Job site for the Elder Villager. Tagged acquirable_job_site, so an unemployed Villager finds
    // and walks to a placed Monolith exactly like any vanilla job block; arriving there is what
    // promotes it (see block/entity/MonolithBlockEntity). Max 1 ticket is the whole "one Elder per
    // Monolith" rule - while an Elder holds this ticket no other Villager will even path towards it.
    public static final RegistryObject<PoiType> MONOLITH_POI = POI_TYPES.register("monolith_poi",
            () -> new PoiType(Set.copyOf(ModBlocks.MONOLITH.get().getStateDefinition().getPossibleStates()), 1, 1));

    // Job site for the Builder profession (Builder Hub construction).
    public static final RegistryObject<PoiType> BLUEPRINT_STAND_POI = POI_TYPES.register("blueprint_stand_poi",
            () -> new PoiType(Set.copyOf(ModBlocks.BLUEPRINT_STAND.get().getStateDefinition().getPossibleStates()), 1, 1));
}