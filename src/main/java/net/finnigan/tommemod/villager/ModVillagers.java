package net.finnigan.tommemod.villager;

import com.google.common.collect.ImmutableSet;
import net.finnigan.tommemod.TommeMod;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
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
}