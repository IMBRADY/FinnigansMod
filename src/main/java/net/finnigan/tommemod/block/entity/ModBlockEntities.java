package net.finnigan.tommemod.block.entity;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.block.ModBlocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, TommeMod.MOD_ID);

    public static final RegistryObject<BlockEntityType<OvenBlockEntity>> OVEN =
            BLOCK_ENTITIES.register("oven", () ->
                    BlockEntityType.Builder.of(OvenBlockEntity::new, ModBlocks.OVEN.get()).build(null));

    public static final RegistryObject<BlockEntityType<MonolithBlockEntity>> MONOLITH =
            BLOCK_ENTITIES.register("monolith", () ->
                    BlockEntityType.Builder.of(MonolithBlockEntity::new, ModBlocks.MONOLITH.get()).build(null));

    public static final RegistryObject<BlockEntityType<ChiefDeskBlockEntity>> CHIEF_DESK =
            BLOCK_ENTITIES.register("chief_desk", () ->
                    BlockEntityType.Builder.of(ChiefDeskBlockEntity::new, ModBlocks.CHIEF_DESK.get()).build(null));

    public static final RegistryObject<BlockEntityType<BuilderHubBlockEntity>> BUILDER_HUB =
            BLOCK_ENTITIES.register("builder_hub", () ->
                    BlockEntityType.Builder.of(BuilderHubBlockEntity::new, ModBlocks.BUILDER_HUB.get()).build(null));

    public static final RegistryObject<BlockEntityType<ConstructionSiteBlockEntity>> CONSTRUCTION_SITE =
            BLOCK_ENTITIES.register("construction_site", () ->
                    BlockEntityType.Builder.of(ConstructionSiteBlockEntity::new, ModBlocks.CONSTRUCTION_BANNER.get()).build(null));
}
