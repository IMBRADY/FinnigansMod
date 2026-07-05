package net.finnigan.tommemod.entity;

import net.finnigan.tommemod.entity.custom.DynamiteEntity;
import net.finnigan.tommemod.entity.custom.MusicNoteEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import static net.finnigan.tommemod.TommeMod.MOD_ID;

public class ModEntityTypes {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MOD_ID);

    public static final RegistryObject<EntityType<DynamiteEntity>> DYNAMITE =
            ENTITY_TYPES.register("dynamite",
                    () -> EntityType.Builder
                            .<DynamiteEntity>of(
                                    DynamiteEntity::new,
                                    MobCategory.MISC)
                            .sized(0.25F, 0.25F)
                            .clientTrackingRange(4)
                            .updateInterval(10)
                            .build("dynamite"));
    public static final RegistryObject<EntityType<MusicNoteEntity>> MUSIC_NOTE =
            ENTITY_TYPES.register("music_note1",
                    () -> EntityType.Builder.<MusicNoteEntity>of(MusicNoteEntity::new, MobCategory.MISC)
                            .sized(1.0F, 1.0F)
                            .build("music_note1"));
}
