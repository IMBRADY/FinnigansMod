package net.finnigan.tommemod.villager;

import com.google.common.collect.ImmutableSet;
import net.finnigan.tommemod.TommeMod;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.Optional;

public class ModVillagers {
    public static final DeferredRegister<VillagerProfession> PROFESSIONS =
            DeferredRegister.create(ForgeRegistries.VILLAGER_PROFESSIONS, TommeMod.MOD_ID);

    public static final RegistryObject<VillagerProfession> BAKER = PROFESSIONS.register("baker",
            () -> new VillagerProfession("baker",
                    holder -> holder.value() == ModPoiTypes.OVEN_POI.get(),
                    holder -> holder.value() == ModPoiTypes.OVEN_POI.get(),
                    ImmutableSet.of(), // requestedItems
                    ImmutableSet.of(), // secondaryPoi
                    SoundEvents.VILLAGER_WORK_FARMER));

    public static final RegistryObject<VillagerProfession> BEEKEEPER = PROFESSIONS.register("beekeeper",
            () -> new VillagerProfession("beekeeper",
                    holder -> holder.is(PoiTypes.BEEHIVE),
                    holder -> holder.is(PoiTypes.BEEHIVE),
                    ImmutableSet.of(), // requestedItems
                    ImmutableSet.of(), // secondaryPoi
                    SoundEvents.BEE_LOOP));

    /**
     * Only ever worn for the single tick between a Villager reaching a Monolith and
     * MonolithBlockEntity swapping it out for an ElderVillagerEntity. It exists because vanilla's
     * AssignProfessionFromJobSite is what does the walking-up-and-claiming we want, and that only
     * fires for a job site some profession actually declares as its own.
     */
    public static final RegistryObject<VillagerProfession> ELDER = PROFESSIONS.register("elder",
            () -> new VillagerProfession("elder",
                    holder -> holder.value() == ModPoiTypes.MONOLITH_POI.get(),
                    holder -> holder.value() == ModPoiTypes.MONOLITH_POI.get(),
                    ImmutableSet.of(), // requestedItems
                    ImmutableSet.of(), // secondaryPoi
                    SoundEvents.VILLAGER_WORK_LIBRARIAN));

    public static final RegistryObject<VillagerProfession> BUILDER = PROFESSIONS.register("builder",
            () -> new VillagerProfession("builder",
                    holder -> holder.value() == ModPoiTypes.BLUEPRINT_STAND_POI.get(),
                    holder -> holder.value() == ModPoiTypes.BLUEPRINT_STAND_POI.get(),
                    ImmutableSet.of(), // requestedItems
                    ImmutableSet.of(), // secondaryPoi
                    SoundEvents.VILLAGER_WORK_MASON));
}