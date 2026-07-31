package net.finnigan.tommemod.effect;

import net.finnigan.tommemod.TommeMod;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModMobEffects {
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, TommeMod.MOD_ID);

    public static final RegistryObject<MobEffect> FROZEN =
            MOB_EFFECTS.register("frozen", FrozenEffect::new);
    public static final RegistryObject<MobEffect> PURIFYING_LIGHT =
            MOB_EFFECTS.register("purifying_light", PurifyingLightEffect::new);
}
