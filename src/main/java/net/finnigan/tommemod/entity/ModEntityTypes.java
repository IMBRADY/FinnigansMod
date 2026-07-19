package net.finnigan.tommemod.entity;

import net.finnigan.tommemod.entity.custom.*;
import net.finnigan.tommemod.entity.custom.ArackopeshHelpers.GrappleHookEntity;
import net.finnigan.tommemod.entity.custom.Bosses.BossCrab.BossCrabEntity;
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
}
