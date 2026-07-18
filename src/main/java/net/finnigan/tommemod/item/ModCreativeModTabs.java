package net.finnigan.tommemod.item;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.block.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModCreativeModTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TommeMod.MOD_ID);

    public static final RegistryObject<CreativeModeTab> MODDED_ITEMS_TAB = CREATIVE_MODE_TABS.register("modded_items_tab",
            () -> CreativeModeTab.builder().icon(() -> new ItemStack(ModItems.DYNAMITE.get()))
                    .title(Component.translatable("creativetab.modded_items_tab"))
                    .displayItems((pParameters, pOutput) -> {
                        // MISC
                        pOutput.accept(ModItems.END_LANTERN.get());
                        pOutput.accept(ModItems.BULLET.get());
                        pOutput.accept(ModItems.DYNAMITE.get());
                        pOutput.accept(ModItems.FIN.get());

                        // WEAPONS
                        pOutput.accept(ModItems.LONGBOW.get());

                        pOutput.accept(ModItems.IRON_CLEAVER.get());
                        pOutput.accept(ModItems.GOLD_CLEAVER.get());
                        pOutput.accept(ModItems.DIAMOND_CLEAVER.get());
                        pOutput.accept(ModItems.NETHERITE_CLEAVER.get());

                        pOutput.accept(ModItems.WOODEN_PIKE.get());
                        pOutput.accept(ModItems.STONE_PIKE.get());
                        pOutput.accept(ModItems.IRON_PIKE.get());
                        pOutput.accept(ModItems.GOLD_PIKE.get());
                        pOutput.accept(ModItems.DIAMOND_PIKE.get());
                        pOutput.accept(ModItems.NETHERITE_PIKE.get());

                        pOutput.accept(ModItems.WOODEN_DAGGER.get());
                        pOutput.accept(ModItems.STONE_DAGGER.get());
                        pOutput.accept(ModItems.IRON_DAGGER.get());
                        pOutput.accept(ModItems.GOLD_DAGGER.get());
                        pOutput.accept(ModItems.DIAMOND_DAGGER.get());
                        pOutput.accept(ModItems.NETHERITE_DAGGER.get());

                        pOutput.accept(ModItems.MUSKET.get());

                        // BLOCKS
                        pOutput.accept(ModBlocks.OVEN.get());

                        // UNIQUES
                        pOutput.accept(ModItems.HARMONY.get());
                        pOutput.accept(ModItems.FEATHERLIGHT.get());
                        pOutput.accept(ModItems.INVERTED_SWORD.get());
                        pOutput.accept(ModItems.LIGHTNING_ROD_SWORD.get());
                        pOutput.accept(ModItems.RANSEUR_OF_UNDEAD.get());
                        pOutput.accept(ModItems.SEER_SWORD.get());
                        pOutput.accept(ModItems.AQUATANA.get());
                        pOutput.accept(ModItems.FIRE_KATANA.get());
                        pOutput.accept(ModItems.BLOSSOM_KATANA.get());
                        pOutput.accept(ModItems.SANGUIS_GLADIO.get());
                        pOutput.accept(ModItems.ARACKOPESH.get());

                        // FOOD
                        pOutput.accept(ModItems.MUSHROOM_MEAT.get());
                        pOutput.accept(ModItems.COOKED_MUSHROOM_MEAT.get());
                    })
                    .build());

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}
