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
}