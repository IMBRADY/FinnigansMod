package net.finnigan.tommemod.loot;

import com.mojang.serialization.Codec;
import net.finnigan.tommemod.TommeMod;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModLootModifiers {
    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> LOOT_MODIFIER_SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, TommeMod.MOD_ID);

    public static final RegistryObject<Codec<AddItemWithChanceModifier>> ADD_ITEM_WITH_CHANCE =
            LOOT_MODIFIER_SERIALIZERS.register("add_item_with_chance", AddItemWithChanceModifier.CODEC);
}
