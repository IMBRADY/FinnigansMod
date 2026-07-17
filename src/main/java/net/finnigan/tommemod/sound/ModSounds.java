package net.finnigan.tommemod.sound;

import net.finnigan.tommemod.TommeMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, TommeMod.MOD_ID);

    public static final RegistryObject<SoundEvent> BOSS_CRAB_LAND = SOUND_EVENTS.register("boss_crab_land",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(TommeMod.MOD_ID, "boss_crab_land")));
}