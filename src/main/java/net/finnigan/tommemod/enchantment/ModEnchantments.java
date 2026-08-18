package net.finnigan.tommemod.enchantment;

import net.finnigan.tommemod.TommeMod;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEnchantments {
    public static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, TommeMod.MOD_ID);

    public static final RegistryObject<Enchantment> LIFE_STEAL =
            ENCHANTMENTS.register("life_steal", LifestealEnchantment::new);
    public static final RegistryObject<Enchantment> POISON =
            ENCHANTMENTS.register("poison", PoisonEnchantment::new);
    public static final RegistryObject<Enchantment> GLOW =
            ENCHANTMENTS.register("glow", GlowEnchantment::new);
    public static final RegistryObject<Enchantment> FLEET =
            ENCHANTMENTS.register("fleet", FleetEnchantment::new);
    public static final RegistryObject<Enchantment> SKYBOUND =
            ENCHANTMENTS.register("skybound", SkyboundEnchantment::new);
    public static final RegistryObject<Enchantment> RESILIENCE =
            ENCHANTMENTS.register("resilience", ResilienceEnchantment::new);
    public static final RegistryObject<Enchantment> IMMUNITY =
            ENCHANTMENTS.register("immunity", ImmunityEnchantment::new);
    public static final RegistryObject<Enchantment> POST_MORTEM =
            ENCHANTMENTS.register("post_mortem", PostMortemEnchantment::new);
    public static final RegistryObject<Enchantment> TOLERANCE =
            ENCHANTMENTS.register("tolerance", ToleranceEnchantment::new);
    public static final RegistryObject<Enchantment> HEROBLADE =
            ENCHANTMENTS.register("heroblade", HerobladeEnchantment::new);
    public static final RegistryObject<Enchantment> ALLPROT =
            ENCHANTMENTS.register("allprot", AllprotEnchantment::new);
}