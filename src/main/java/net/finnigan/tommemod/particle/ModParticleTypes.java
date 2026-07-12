package net.finnigan.tommemod.particle;

import net.finnigan.tommemod.TommeMod;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModParticleTypes {
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, TommeMod.MOD_ID);

    public static final RegistryObject<SimpleParticleType> AQUATANA_PARTICLE =
            PARTICLE_TYPES.register("aquatana_particle", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> FIRE_LARGE_1 = PARTICLE_TYPES.register("large1", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> FIRE_LARGE_2 = PARTICLE_TYPES.register("large2", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> FIRE_SMALL_1 = PARTICLE_TYPES.register("small1", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> FIRE_SMALL_2 = PARTICLE_TYPES.register("small2", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> WAVE_1 = PARTICLE_TYPES.register("wave1", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> WAVE_2 = PARTICLE_TYPES.register("wave2", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> WAVE_3 = PARTICLE_TYPES.register("wave3", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> WAVE_4 = PARTICLE_TYPES.register("wave4", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> WAVE_5 = PARTICLE_TYPES.register("wave5", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> FOAM_1 = PARTICLE_TYPES.register("foam1", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> FOAM_2 = PARTICLE_TYPES.register("foam2", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> FOAM_3 = PARTICLE_TYPES.register("foam3", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> FOAM_4 = PARTICLE_TYPES.register("foam4", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> FOAM_5 = PARTICLE_TYPES.register("foam5", () -> new SimpleParticleType(false));
}