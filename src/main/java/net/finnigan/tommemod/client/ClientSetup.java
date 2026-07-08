package net.finnigan.tommemod.client;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.client.screen.OvenScreen;
import net.finnigan.tommemod.entity.ModEntityTypes;
import net.finnigan.tommemod.entity.custom.ButterflyEntity;
import net.finnigan.tommemod.entity.custom.JellyfishEntity;
import net.finnigan.tommemod.entity.custom.MushlingEntity;
import net.finnigan.tommemod.item.ModItems;
import net.finnigan.tommemod.menu.ModMenuTypes;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.api.distmarker.Dist;

@Mod.EventBusSubscriber(modid = "tommemod", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemProperties.register(ModItems.LONGBOW.get(), new ResourceLocation("pull"),
                    (stack, level, entity, seed) -> {
                        if (entity == null || entity.getUseItem() != stack) return 0.0F;
                        return (stack.getUseDuration() - entity.getUseItemRemainingTicks()) / 50.0F;
                    });

            ItemProperties.register(ModItems.LONGBOW.get(), new ResourceLocation("pulling"),
                    (stack, level, entity, seed) ->
                            entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1.0F : 0.0F);
            MenuScreens.register(ModMenuTypes.OVEN_MENU.get(), OvenScreen::new);
            SpawnPlacements.register(
                    ModEntityTypes.JELLYFISH.get(),
                    SpawnPlacements.Type.IN_WATER,
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    JellyfishEntity::checkSurfaceWaterAnimalSpawnRules);
        });
    }
    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntityTypes.JELLYFISH.get(), JellyfishEntity.createAttributes().build());
        event.put(ModEntityTypes.BUTTERFLY.get(), ButterflyEntity.createAttributes().build());
        event.put(ModEntityTypes.MUSHLING.get(), MushlingEntity.createAttributes().build());
    }

    @Mod.EventBusSubscriber(modid = TommeMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientEvents {
        @SubscribeEvent
        public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(ModEntityTypes.JELLYFISH.get(), JellyfishRenderer::new);
            event.registerEntityRenderer(ModEntityTypes.BUTTERFLY.get(), ButterflyRenderer::new);
            event.registerEntityRenderer(ModEntityTypes.MUSHLING.get(), MushlingRenderer::new);
        }
    }

    @SubscribeEvent
    public static void registerSpawnPlacements(SpawnPlacementRegisterEvent event) {
        event.register(ModEntityTypes.MUSHLING.get(),
                SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                MushlingEntity::checkMushlingSpawnRules,
                SpawnPlacementRegisterEvent.Operation.REPLACE);
    }
}