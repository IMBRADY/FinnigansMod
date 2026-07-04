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
            ITEMS.register("iron_cleaver", // has no cool sound effect, needs transformations updated
                    () -> new ModdedSwordItem(
                            Tiers.IRON,
                            10,
                            -3.4F,
                            new Item.Properties().stacksTo(1)
                    ));
    public static final RegistryObject<Item> GOLD_CLEAVER =
            ITEMS.register("gold_cleaver", // has no cool sound effect, needs transformations updated
                    () -> new ModdedSwordItem(
                            Tiers.GOLD,
                            10,
                            -3.4F,
                            new Item.Properties().stacksTo(1)
                    ));
    public static final RegistryObject<Item> DIAMOND_CLEAVER =
            ITEMS.register("diamond_cleaver", // has no cool sound effect, needs transformations updated
                    () -> new ModdedSwordItem(
                            Tiers.DIAMOND,
                            12,
                            -3.4F,
                            new Item.Properties().stacksTo(1)
                    ));
    public static final RegistryObject<Item> NETHERITE_CLEAVER =
            ITEMS.register("netherite_cleaver", // has no cool sound effect, needs transformations updated
                    () -> new ModdedSwordItem(
                            Tiers.NETHERITE,
                            13,
                            -3.4F,
                            new Item.Properties().stacksTo(1)
                    ));
    public static final RegistryObject<Item> WOOD_DAGGER =
            ITEMS.register("wood_dagger", // has no cool sound effect, needs transformations updated
                    () -> new ModdedSwordItem(
                            Tiers.WOOD,
                            0,
                            -1.6F,
                            new Item.Properties().stacksTo(1)
                    ));
    public static final RegistryObject<Item> STONE_DAGGER =
            ITEMS.register("stone_dagger", // has no cool sound effect, needs transformations updated
                    () -> new ModdedSwordItem(
                            Tiers.STONE,
                            0,
                            -1.6F,
                            new Item.Properties().stacksTo(1)
                    ));
    public static final RegistryObject<Item> IRON_DAGGER =
            ITEMS.register("iron_dagger", // has no cool sound effect, needs transformations updated
                    () -> new ModdedSwordItem(
                            Tiers.IRON,
                            0,
                            -1.6F,
                            new Item.Properties().stacksTo(1)
                    ));
    public static final RegistryObject<Item> GOLD_DAGGER =
            ITEMS.register("gold_dagger", // has no cool sound effect, needs transformations updated
                    () -> new ModdedSwordItem(
                            Tiers.GOLD,
                            0,
                            -1.6F,
                            new Item.Properties().stacksTo(1)
                    ));
    public static final RegistryObject<Item> DIAMOND_DAGGER =
            ITEMS.register("diamond_dagger", // has no cool sound effect, needs transformations updated
                    () -> new ModdedSwordItem(
                            Tiers.DIAMOND,
                            0,
                            -1.6F,
                            new Item.Properties().stacksTo(1)
                    ));
    public static final RegistryObject<Item> NETHERITE_DAGGER =
            ITEMS.register("netherite_dagger", // has no cool sound effect, needs transformations updated
                    () -> new ModdedSwordItem(
                            Tiers.NETHERITE,
                            0,
                            -1.6F,
                            new Item.Properties().stacksTo(1)
                    ));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }

}
