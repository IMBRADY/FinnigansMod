package net.finnigan.tommemod.block;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.block.custom.ArmageddonBlock;
import net.finnigan.tommemod.block.custom.BuilderHubBlock;
import net.finnigan.tommemod.block.custom.ConstructionBannerBlock;
import net.finnigan.tommemod.block.custom.MonolithBlock;
import net.finnigan.tommemod.block.custom.OvenBlock;
import net.finnigan.tommemod.item.ModItems;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, TommeMod.MOD_ID);

    public static final RegistryObject<Block> OVEN = registerBlock("oven",
            () -> new OvenBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(3.5F)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops()));
    // Default tnt blast radius is 4.0
    private static final float ARMAGEDDON_BLAST_RADIUS = 60.0F;

    public static final RegistryObject<Block> ARMAGEDDON = registerBlock(
            "armageddon",
            () -> new ArmageddonBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.STONE)
                            .strength(3.5F)
                            .sound(SoundType.STONE)
                            .requiresCorrectToolForDrops(),
                    ARMAGEDDON_BLAST_RADIUS
            )
    );

    public static final RegistryObject<Block> MONOLITH = registerBlock("monolith",
            () -> new MonolithBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(10.0F)
                    .sound(SoundType.AMETHYST)
                    .requiresCorrectToolForDrops()));

    // Job-site block for the Builder profession - deliberately a plain Block (no custom class, no
    // BlockEntity/GUI needed; it only exists to be a claimable POI, see villager/ModPoiTypes.java).
    public static final RegistryObject<Block> BLUEPRINT_STAND = registerBlock("blueprint_stand",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5F)
                    .sound(SoundType.WOOD)));

    public static final RegistryObject<Block> BUILDER_HUB = registerBlock("builder_hub",
            () -> new BuilderHubBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_ORANGE)
                    .strength(10.0F)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops()));

    public static final RegistryObject<Block> CONSTRUCTION_BANNER = registerBlock("construction_banner",
            () -> new ConstructionBannerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.SNOW)
                    .noCollission()
                    .strength(1.0F)
                    .sound(SoundType.WOOL)));

    private static <T extends Block> RegistryObject<T> registerBlock(String name, Supplier<T> block) {
        RegistryObject<T> toReturn = BLOCKS.register(name, block);
        registerBlockItem(name, toReturn);
        return toReturn;
    }

    private static <T extends Block> void registerBlockItem(String name, RegistryObject<T> block) {
        ModItems.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }
}