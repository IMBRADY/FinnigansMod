package net.finnigan.tommemod.item;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.entity.custom.ModdedSwordItem;
import net.finnigan.tommemod.item.custom.DynamiteItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Tiers;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, TommeMod.MOD_ID);

    public static final RegistryObject<Item> DYNAMITE = ITEMS.register("dynamite",
            () -> new DynamiteItem(new Item.Properties()));
    public static final RegistryObject<Item> BULLET = ITEMS.register("bullet",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> IRON_CLEAVER =
            ITEMS.register("iron_cleaver",
                    () -> new ModdedSwordItem(
                            Tiers.IRON,
                            12,
                            -1.2F,
                            new Item.Properties().stacksTo(1)
                    ));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }

}
