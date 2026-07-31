package net.finnigan.tommemod.entity;

import net.finnigan.tommemod.entity.custom.*;
import net.finnigan.tommemod.entity.custom.AmethystCutlassHelpers.AmethystBeamEntity;
import net.finnigan.tommemod.entity.custom.ArackopeshHelpers.GrappleHookEntity;
import net.finnigan.tommemod.entity.custom.ColletisHelpers.ColletisVineEntity;
import net.finnigan.tommemod.entity.custom.Bosses.BossCrab.BossCrabEntity;
import net.finnigan.tommemod.entity.custom.IxeHelpers.IxeBoxEntity;
import net.finnigan.tommemod.entity.custom.IxeHelpers.IxeProjectileEntity;
import net.finnigan.tommemod.entity.custom.EndScytheHelpers.EndScytheProjectileEntity;
import net.finnigan.tommemod.entity.custom.LumapierHelpers.LightBoltProjectileEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import static net.finnigan.tommemod.TommeMod.MOD_ID;
import net.finnigan.tommemod.entity.custom.EndLanternEntity;


public class ModEntityTypes {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MOD_ID);

    public static final RegistryObject<EntityType<BossCrabEntity>> BOSS_CRAB =
            ENTITY_TYPES.register("boss_crab", () -> EntityType.Builder.of(BossCrabEntity::new, MobCategory.MONSTER)
                    .sized(3.6f, 2.0f) // hitbox
                    .updateInterval(1)
                    .build("boss_crab"));

    public static final RegistryObject<EntityType<IxeProjectileEntity>> IXE_PROJECTILE =
            ENTITY_TYPES.register("ixe_projectile", () -> EntityType.Builder.<IxeProjectileEntity>of(IxeProjectileEntity::new, MobCategory.MISC)
                    .sized(0.35F, 0.35F)
                    .clientTrackingRange(64)
                    .updateInterval(1)
                    .noSave()
                    .build("ixe_projectile"));

    public static final RegistryObject<EntityType<IxeBoxEntity>> IXE_BOX =
            ENTITY_TYPES.register("ixe_box", () -> EntityType.Builder.<IxeBoxEntity>of(IxeBoxEntity::new, MobCategory.MISC)
                    .sized(1.0F, 2.0F)
                    .clientTrackingRange(64)
                    .updateInterval(3)
                    .noSave()
                    .build("ixe_box"));

    public static final RegistryObject<EntityType<EndScytheProjectileEntity>> END_SCYTHE_PROJECTILE =
            ENTITY_TYPES.register("end_scythe_projectile", () -> EntityType.Builder.<EndScytheProjectileEntity>of(EndScytheProjectileEntity::new, MobCategory.MISC)
                    .sized(0.35F, 0.35F)
                    .clientTrackingRange(64)
                    .updateInterval(1)
                    .noSave()
                    .build("end_scythe_projectile"));

    public static final RegistryObject<EntityType<AmethystBeamEntity>> AMETHYST_BEAM =
            ENTITY_TYPES.register("amethyst_beam", () -> EntityType.Builder.<AmethystBeamEntity>of(AmethystBeamEntity::new, MobCategory.MISC)
                    .sized(0.1F, 0.1F)
                    .noSave()
                    .clientTrackingRange(64)
                    .updateInterval(1)
                    .build("amethyst_beam"));
    public static final RegistryObject<EntityType<DynamiteEntity>> DYNAMITE =
            ENTITY_TYPES.register("dynamite",
                    () -> EntityType.Builder
                            .<DynamiteEntity>of(
                                    DynamiteEntity::new, MobCategory.MISC)
                            .sized(0.25F, 0.25F)
                            .clientTrackingRange(4) //how close (in blocks) entity sends packets to player (if player outside range, entity could pop out of existence (not necessarily despawn) if range too big, takes up too much bandwidth)
                            .updateInterval(10) //how frequently (in ticks) entity updates (high num = less cpu usage, more choppy)
                            .build("dynamite"));
    public static final RegistryObject<EntityType<MusicNoteEntity>> MUSIC_NOTE =
            ENTITY_TYPES.register("music_note1",
                    () -> EntityType.Builder.<MusicNoteEntity>of(MusicNoteEntity::new, MobCategory.MISC)
                            .sized(1.0F, 1.0F)
                            .build("music_note1"));
    public static final RegistryObject<EntityType<GiantSwordEntity>> GIANT_SWORD = ENTITY_TYPES.register("giant_sword",
            () -> EntityType.Builder.of(GiantSwordEntity::new, MobCategory.MISC)
                    .sized(1.5F, 4.0F) // adjust to match giant sword's visual
                    .clientTrackingRange(64)
                    .updateInterval(1)
                    .build("giant_sword"));
    public static final RegistryObject<EntityType<JellyfishEntity>> JELLYFISH =
            ENTITY_TYPES.register("jellyfish", () -> EntityType.Builder.of(JellyfishEntity::new, MobCategory.WATER_CREATURE)
                    .sized(0.6f, 2.0f) // Width, Height of hitbox
                    .clientTrackingRange(8)
                    .build("jellyfish"));
    public static final RegistryObject<EntityType<ButterflyEntity>> BUTTERFLY =
            ENTITY_TYPES.register("butterfly", () -> EntityType.Builder.of(ButterflyEntity::new, MobCategory.CREATURE)
                    .sized(0.4f, 0.3f)
                    .clientTrackingRange(8) // Distance in chunks when mob renders (large mobs = large number)
                    .build("butterfly"));
    public static final RegistryObject<EntityType<MushlingEntity>> MUSHLING =
            ENTITY_TYPES.register("mushling", () -> EntityType.Builder.of(MushlingEntity::new, MobCategory.CREATURE)
                    .sized(0.8f, 0.8f)
                    .clientTrackingRange(8)
                    .build("mushling"));
    public static final RegistryObject<EntityType<EndLanternEntity>> END_LANTERN =
            ENTITY_TYPES.register("end_lantern", () -> EntityType.Builder.of(EndLanternEntity::new, MobCategory.CREATURE)
                    .sized(0.8f, 1.5f)
                    .clientTrackingRange(8)
                    .build("end_lantern"));
    public static final RegistryObject<EntityType<GrappleHookEntity>> GRAPPLE_HOOK = ENTITY_TYPES.register("grapple_hook",
            () -> EntityType.Builder.<GrappleHookEntity>of(GrappleHookEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(64)
                    .updateInterval(10)
                    .build("grapple_hook"));
    public static final RegistryObject<EntityType<ColletisVineEntity>> COLLETIS_VINE = ENTITY_TYPES.register("colletis_vine",
            () -> EntityType.Builder.<ColletisVineEntity>of(ColletisVineEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(64)
                    .updateInterval(10)
                    .build("colletis_vine"));
    public static final RegistryObject<EntityType<CapybaraEntity>> CAPYBARA =
            ENTITY_TYPES.register("capybara", () -> EntityType.Builder.of(CapybaraEntity::new, MobCategory.CREATURE)
                    .sized(0.8F, 0.6F)
                    .clientTrackingRange(8)
                    .updateInterval(3)
                    .build("capybara"));
    public static final RegistryObject<EntityType<MantaEntity>> MANTA = ENTITY_TYPES.register("manta",
            () -> EntityType.Builder.of(MantaEntity::new, MobCategory.WATER_AMBIENT)
                    .sized(1.1f, 0.3f)
                    .clientTrackingRange(8)
                    .updateInterval(3)
                    .build("manta"));
    public static final RegistryObject<EntityType<TigerEntity>> TIGER = ENTITY_TYPES.register("tiger",
            () -> EntityType.Builder.of(TigerEntity::new, MobCategory.CREATURE)
                    .sized(1.4f, 1.4f)
                    .clientTrackingRange(8)
                    .updateInterval(3)
                    .build("tiger"));
    public static final RegistryObject<EntityType<BirdieEntity>> BIRDIE = ENTITY_TYPES.register("birdie",
            () -> EntityType.Builder.of(BirdieEntity::new, MobCategory.CREATURE)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(8)
                    .updateInterval(3)
                    .build("birdie"));
    public static final RegistryObject<EntityType<SeagullEntity>> SEAGULL = ENTITY_TYPES.register("seagull",
            () -> EntityType.Builder.of(SeagullEntity::new, MobCategory.CREATURE)
                    .sized(0.6f, 0.6f)
                    .clientTrackingRange(8)
                    .updateInterval(3)
                    .build("seagull"));
    public static final RegistryObject<EntityType<DungeonCrabEntity>> DUNGEON_CRAB = ENTITY_TYPES.register("dungeon_crab",
            () -> EntityType.Builder.of(DungeonCrabEntity::new, MobCategory.MONSTER)
                    .sized(1.0f, 1.0f)
                    .clientTrackingRange(8)
                    .updateInterval(3)
                    .build("dungeon_crab"));
    public static final RegistryObject<EntityType<LivingArmorEntity>> LIVING_ARMOR =
            ENTITY_TYPES.register("living_armor", () -> EntityType.Builder.of(LivingArmorEntity::new, MobCategory.MONSTER)
                    .sized(0.8F, 2.2F)
                    .clientTrackingRange(8)
                    .updateInterval(3)
                    .build("living_armor"));
    public static final RegistryObject<EntityType<DuckEntity>> DUCK =
            ENTITY_TYPES.register("duck", () -> EntityType.Builder.of(DuckEntity::new, MobCategory.CREATURE)
                    .sized(0.5F, 0.6F)
                    .clientTrackingRange(8)
                    .updateInterval(3)
                    .build("duck"));
    public static final RegistryObject<EntityType<CrabEntity>> CRAB =
            ENTITY_TYPES.register("crab", () -> EntityType.Builder.of(CrabEntity::new, MobCategory.CREATURE)
                    .sized(0.5F, 0.3F)
                    .clientTrackingRange(8)
                    .updateInterval(3)
                    .build("crab"));
    public static final RegistryObject<EntityType<HermitCrabEntity>> HERMIT_CRAB =
            ENTITY_TYPES.register("hermit_crab", () -> EntityType.Builder.of(HermitCrabEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 0.6F)
                    .clientTrackingRange(8)
                    .updateInterval(3)
                    .build("hermit_crab"));
    public static final RegistryObject<EntityType<BeeNadeEntity>> BEE_NADE =
            ENTITY_TYPES.register("bee_nade", () -> EntityType.Builder.<BeeNadeEntity>of(BeeNadeEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .build("bee_nade"));
    public static final RegistryObject<EntityType<ElderVillagerEntity>> ELDER_VILLAGER =
            ENTITY_TYPES.register("elder_villager", () -> EntityType.Builder.of(ElderVillagerEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(8)
                    .updateInterval(3)
                    .build("elder_villager"));
    public static final RegistryObject<EntityType<WarriorVillagerEntity>> WARRIOR_VILLAGER =
            ENTITY_TYPES.register("warrior_villager", () -> EntityType.Builder.of(WarriorVillagerEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(8)
                    .updateInterval(3)
                    .build("warrior_villager"));
    public static final RegistryObject<EntityType<ScarecrowEntity>> SCARECROW =
            ENTITY_TYPES.register("scarecrow", () -> EntityType.Builder.of(ScarecrowEntity::new, MobCategory.MONSTER)
                    .sized(0.8F, 2.6F)
                    .clientTrackingRange(8)
                    .updateInterval(3)
                    .build("scarecrow"));
    public static final RegistryObject<EntityType<CyclopsEntity>> CYCLOPS =
            ENTITY_TYPES.register("cyclops", () -> EntityType.Builder.of(CyclopsEntity::new, MobCategory.MONSTER)
                    .sized(0.9F, 2.3F)
                    .clientTrackingRange(8)
                    .updateInterval(3)
                    .build("cyclops"));
    public static final RegistryObject<EntityType<HammerheadSharkEntity>> HAMMERHEAD_SHARK =
            ENTITY_TYPES.register("hammerhead", () -> EntityType.Builder.of(HammerheadSharkEntity::new, MobCategory.WATER_CREATURE)
                    .sized(1.4F, 1.0F)
                    .clientTrackingRange(8)
                    .updateInterval(3)
                    .build("hammerhead"));
    public static final RegistryObject<EntityType<DaggerEntity>> DAGGER =
            ENTITY_TYPES.register("dagger", () -> EntityType.Builder.<DaggerEntity>of(DaggerEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .build("dagger"));

    public static final RegistryObject<EntityType<LightBoltProjectileEntity>> LIGHT_BOLT_PROJECTILE =
            ENTITY_TYPES.register("light_bolt_projectile", () -> EntityType.Builder.<LightBoltProjectileEntity>of(LightBoltProjectileEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(64)
                    .updateInterval(1)
                    .noSave()
                    .build("light_bolt_projectile"));
}
