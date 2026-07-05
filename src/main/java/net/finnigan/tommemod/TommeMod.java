package net.finnigan.tommemod;

import com.mojang.logging.LogUtils;
import net.finnigan.tommemod.block.ModBlocks;
import net.finnigan.tommemod.block.entity.ModBlockEntities;
import net.finnigan.tommemod.entity.ModEntityTypes;
import net.finnigan.tommemod.item.ModCreativeModTabs;
import net.finnigan.tommemod.item.ModItems;
import net.finnigan.tommemod.menu.ModMenuTypes;
import net.finnigan.tommemod.villager.ModPoiTypes;
import net.finnigan.tommemod.villager.ModVillagers;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(TommeMod.MOD_ID)
public class TommeMod
{
    // Define mod id in a common place for everything to reference
    public static final String MOD_ID = "tommemod"; // Shift + F6 to change all instances of this
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();

    public TommeMod()
    {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModCreativeModTabs.register(modEventBus);
        ModEntityTypes.ENTITY_TYPES.register(modEventBus);
        ModItems.register(modEventBus);

        ModBlocks.BLOCKS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModMenuTypes.MENUS.register(modEventBus);

        ModPoiTypes.POI_TYPES.register(modEventBus);
        ModVillagers.PROFESSIONS.register(modEventBus);

        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);

        // Register the item to a creative tab
        modEventBus.addListener(this::addCreative);
    }

    private void commonSetup(final FMLCommonSetupEvent event)
    {

    }

    // Add the example block item to the building blocks tab
    private void addCreative(BuildCreativeModeTabContentsEvent event){
        if(event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.DYNAMITE);
        }
        if(event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(ModItems.BULLET);

            event.accept(ModItems.IRON_CLEAVER);
            event.accept(ModItems.GOLD_CLEAVER);
            event.accept(ModItems.DIAMOND_CLEAVER);
            event.accept(ModItems.NETHERITE_CLEAVER);

            event.accept(ModItems.WOOD_DAGGER);
            event.accept(ModItems.STONE_DAGGER);
            event.accept(ModItems.IRON_DAGGER);
            event.accept(ModItems.GOLD_DAGGER);
            event.accept(ModItems.DIAMOND_DAGGER);
            event.accept(ModItems.NETHERITE_DAGGER);

            event.accept(ModItems.MUSKET);
        }
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event)
    {

    }

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents
    {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event)
        {

        }
    }
}
