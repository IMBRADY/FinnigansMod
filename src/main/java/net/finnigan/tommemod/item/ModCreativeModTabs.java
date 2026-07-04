package net.finnigan.tommemod.item;

import net.finnigan.tommemod.TommeMod;
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
                        pOutput.accept(ModItems.BULLET.get());
                        pOutput.accept(ModItems.DYNAMITE.get());
                        pOutput.accept(ModItems.IRON_CLEAVER.get());
                    })
                    .build());

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}
