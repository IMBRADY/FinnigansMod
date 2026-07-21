package net.finnigan.tommemod.item;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.entity.custom.ModdedSwordItem;
import net.finnigan.tommemod.item.custom.*;
import net.finnigan.tommemod.item.custom.totems.*;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Tiers;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, TommeMod.MOD_ID);

    public static final RegistryObject<Item> LONGBOW = ITEMS.register("longbow",
            () -> new LongbowItem(new Item.Properties()
                    .durability(450) // tweak durability vs vanilla bow's 384
            ));
    public static final RegistryObject<Item> DYNAMITE = ITEMS.register("dynamite",
            () -> new DynamiteItem(new Item.Properties()));

    public static final RegistryObject<Item> SANGUIS_GLADIO = ITEMS.register("sanguis_gladio",
            () -> new SanguisGladioItem(
                    Tiers.NETHERITE,
                    25,
                    -2.4F,
                    new Item.Properties()));
    public static final RegistryObject<Item> ARACKOPESH = ITEMS.register("arackopesh",
            () -> new ArackopeshItem(Tiers.NETHERITE,
                    20,
                    -2.6F,
                    new Item.Properties()));
    public static final RegistryObject<Item> SEER_SWORD = ITEMS.register("seer_sword",
            () -> new SeerSwordItem(
                    Tiers.NETHERITE,
                    20,
                    -2.6F,
                    new Item.Properties()));
    public static final RegistryObject<Item> HARMONY = ITEMS.register("harmony",
            () -> new HarmonyItem(
                    Tiers.NETHERITE,
                    22,
                    -2.6f,
                    new Item.Properties()));
    public static final RegistryObject<Item> RANSEUR_OF_UNDEAD = ITEMS.register("ranseur_of_undead",
            () -> new RanseurOfUndeadSwordItem(
                    Tiers.NETHERITE,
                    20,
                    -2.6f,
                    new Item.Properties()));
    public static final RegistryObject<LightningRodSwordItem> LIGHTNING_ROD_SWORD = ITEMS.register("lightning_rod_sword", () ->
            new LightningRodSwordItem(Tiers.NETHERITE,
                    20,
                    -2.6F,
                    new Item.Properties()));
    public static final RegistryObject<Item> MUSIC_NOTE_ITEM = ITEMS.register("music_note_item1",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> FEATHERLIGHT = ITEMS.register("featherlight",
            () -> new FeatherlightItem(Tiers.NETHERITE,
                    15,
                    -1.6F,
                    new Item.Properties()));
    public static final RegistryObject<InvertedSwordItem> INVERTED_SWORD = ITEMS.register("inverted_sword", () ->
            new InvertedSwordItem(Tiers.NETHERITE,
                    20,
                    -2.6F,
                    new Item.Properties()));
    public static final RegistryObject<Item> AQUATANA = ITEMS.register("aquatana",
            () -> new AquatanaItem(
                    Tiers.NETHERITE,
                    20,
                    -2.4F,
                    new Item.Properties()));
    public static final RegistryObject<Item> BLOSSOM_KATANA = ITEMS.register("blossom_katana",
            () -> new BlossomKatanaItem(
                    Tiers.NETHERITE,
                    20,
                    -2.4F,
                    new Item.Properties()));
    public static final RegistryObject<Item> FIRE_KATANA = ITEMS.register("fire_katana",
            () -> new FireKatanaItem(
                    Tiers.NETHERITE,
                    20,
                    -2.4F,
                    new Item.Properties()));

    public static final RegistryObject<PikeItem> WOODEN_PIKE = ITEMS.register("wooden_pike", () ->
            new PikeItem(
                    Tiers.WOOD,
                    3,          // attackDamageModifier
                    -3.0F,    // attackSpeedModifier
                    1.5,        // reachBonus (add on top of default 4.5)
                    1.0F,       // knockbackBonus
                    new Item.Properties()
            )
    );
    public static final RegistryObject<PikeItem> STONE_PIKE = ITEMS.register("stone_pike", () ->
            new PikeItem(
                    Tiers.STONE,
                    3,          // attackDamageModifier
                    -3.0F,    // attackSpeedModifier
                    1.5,        // reachBonus (add on top of default 4.5)
                    1.0F,       // knockbackBonus
                    new Item.Properties()
            )
    );
    public static final RegistryObject<PikeItem> IRON_PIKE = ITEMS.register("iron_pike", () ->
            new PikeItem(
                    Tiers.IRON,
                    3,          // attackDamageModifier
                    -3.0F,    // attackSpeedModifier
                    1.5,        // reachBonus (add on top of default 4.5)
                    1.0F,       // knockbackBonus
                    new Item.Properties()
            )
    );
    public static final RegistryObject<PikeItem> GOLD_PIKE = ITEMS.register("gold_pike", () ->
            new PikeItem(
                    Tiers.GOLD,
                    3,          // attackDamageModifier
                    -3.0F,    // attackSpeedModifier
                    1.5,        // reachBonus (add on top of default 4.5)
                    1.0F,       // knockbackBonus
                    new Item.Properties()
            )
    );
    public static final RegistryObject<PikeItem> DIAMOND_PIKE = ITEMS.register("diamond_pike", () ->
            new PikeItem(
                    Tiers.DIAMOND,
                    3,          // attackDamageModifier
                    -3.0F,    // attackSpeedModifier
                    1.5,        // reachBonus (add on top of default 4.5)
                    1.0F,       // knockbackBonus
                    new Item.Properties()
            )
    );
    public static final RegistryObject<PikeItem> NETHERITE_PIKE = ITEMS.register("netherite_pike", () ->
            new PikeItem(
                    Tiers.NETHERITE,
                    3,          // attackDamageModifier
                    -3.0F,    // attackSpeedModifier
                    1.5,        // reachBonus (add on top of default 4.5)
                    1.0F,       // knockbackBonus
                    new Item.Properties()
            )
    );

    public static final RegistryObject<Item> TOTEM_OF_LUCKY_DICE = ITEMS.register("totem_of_lucky_dice",
            () -> new TotemOfLuckyDiceItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> TOTEM_OF_THE_SUN = ITEMS.register("totem_of_the_sun",
            () -> new TotemOfTheSunItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> TOTEM_OF_THE_MOON = ITEMS.register("totem_of_the_moon",
            () -> new TotemOfTheMoonItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> TOTEM_OF_TRIGGER_FINGER = ITEMS.register("totem_of_trigger_finger",
            () -> new TotemOfTriggerfingerItem(new Item.Properties().stacksTo(1))); // pending your cooldown code
    public static final RegistryObject<Item> TOTEM_OF_WRATH = ITEMS.register("totem_of_wrath",
            () -> new TotemOfWrathItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> TOTEM_OF_DOUBLE_STRIKE = ITEMS.register("totem_of_double_strike",
            () -> new TotemOfDoublestrikeItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> TOTEM_OF_BLOODLUST = ITEMS.register("totem_of_bloodlust",
            () -> new TotemOfBloodlustItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> TOTEM_OF_THE_UNDEAD = ITEMS.register("totem_of_the_undead",
            () -> new TotemOfTheUndeadItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> TOTEM_OF_FASTING = ITEMS.register("totem_of_fasting",
            () -> new TotemOfFastingItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> TOTEM_OF_MAINTENANCE = ITEMS.register("totem_of_maintenance",
            () -> new TotemOfMaintenanceItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> TOTEM_OF_GREED = ITEMS.register("totem_of_greed",
            () -> new TotemOfGreedItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> TOTEM_OF_NIGHT_VISION = ITEMS.register("totem_of_night_vision",
            () -> new TotemOfNightVisionItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> TOTEM_OF_THE_PHOENIX = ITEMS.register("totem_of_the_phoenix",
            () -> new TotemOfThePhoenixItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> TOTEM_OF_FIRST_AID = ITEMS.register("totem_of_first_aid",
            () -> new TotemOfFirstAidItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> TOTEM_OF_KINSHIP = ITEMS.register("totem_of_kinship",
            () -> new TotemOfKinshipItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> MUSKET = ITEMS.register("musket",
            () -> new MusketItem(new Item.Properties()));
    public static final RegistryObject<Item> BULLET = ITEMS.register("bullet",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> END_LANTERN = ITEMS.register("end_lantern",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> FIN = ITEMS.register("fin",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> ANCIENT_ARMOR_FRAGMENT = ITEMS.register("ancient_armor_fragment",
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

    public static final RegistryObject<Item> WOODEN_DAGGER =
            ITEMS.register("wooden_dagger", // has no cool sound effect, needs transformations updated
                    () -> new ModdedSwordItem(
                            Tiers.WOOD,
                            0,
                            -1.0F,
                            new Item.Properties().stacksTo(1)
                    ));
    public static final RegistryObject<Item> STONE_DAGGER =
            ITEMS.register("stone_dagger", // has no cool sound effect, needs transformations updated
                    () -> new ModdedSwordItem(
                            Tiers.STONE,
                            0,
                            -1.0F,
                            new Item.Properties().stacksTo(1)
                    ));
    public static final RegistryObject<Item> IRON_DAGGER =
            ITEMS.register("iron_dagger", // has no cool sound effect, needs transformations updated
                    () -> new ModdedSwordItem(
                            Tiers.IRON,
                            0,
                            -1.0F,
                            new Item.Properties().stacksTo(1)
                    ));
    public static final RegistryObject<Item> GOLD_DAGGER =
            ITEMS.register("gold_dagger", // has no cool sound effect, needs transformations updated
                    () -> new ModdedSwordItem(
                            Tiers.GOLD,
                            0,
                            -1.0F,
                            new Item.Properties().stacksTo(1)
                    ));
    public static final RegistryObject<Item> DIAMOND_DAGGER =
            ITEMS.register("diamond_dagger", // has no cool sound effect, needs transformations updated
                    () -> new ModdedSwordItem(
                            Tiers.DIAMOND,
                            0,
                            -1.0F,
                            new Item.Properties().stacksTo(1)
                    ));
    public static final RegistryObject<Item> NETHERITE_DAGGER =
            ITEMS.register("netherite_dagger", // has no cool sound effect, needs transformations updated
                    () -> new ModdedSwordItem(
                            Tiers.NETHERITE,
                            0,
                            -1.0F,
                            new Item.Properties().stacksTo(1)
                    ));

    public static final RegistryObject<Item> MUSHROOM_MEAT = ITEMS.register("mushroom_meat",
            () -> new Item(new Item.Properties().food(new FoodProperties.Builder()
                    .nutrition(2)
                    .saturationMod(0.4f)
                    .effect(() -> new MobEffectInstance(MobEffects.BLINDNESS, 100, 0), 1.0f)
                    .effect(() -> new MobEffectInstance(MobEffects.POISON, 40, 0), 1.0f)
                    .meat()
                    .build())));
    public static final RegistryObject<Item> COOKED_MUSHROOM_MEAT = ITEMS.register("cooked_mushroom_meat",
            () -> new Item(new Item.Properties().food(new FoodProperties.Builder()
                    .nutrition(6)
                    .saturationMod(9.6f)
                    .effect(() -> new MobEffectInstance(MobEffects.REGENERATION, 30, 0), 1.0f)
                    .meat()
                    .build())));
    public static final RegistryObject<Item> BIRD_MEAT = ITEMS.register("bird_meat",
            () -> new Item(new Item.Properties().food(new FoodProperties.Builder()
                    .saturationMod(0.4f)
                    .nutrition(2)
                    .build())));
    public static final RegistryObject<Item> COOKED_BIRD_MEAT = ITEMS.register("cooked_bird_meat",
            () -> new Item(new Item.Properties().food(new FoodProperties.Builder()
                    .saturationMod(4.0f)
                    .nutrition(5)
                    .build())));



    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }

}
