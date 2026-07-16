package net.finnigan.tommemod.client;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.client.particle.AquatanaParticle;
import net.finnigan.tommemod.client.particle.FireRingParticle;
import net.finnigan.tommemod.client.renderer.GiantSwordRenderer;
import net.finnigan.tommemod.client.renderer.GrappleHookRenderer;
import net.finnigan.tommemod.client.renderer.MusicNoteRenderer;
import net.finnigan.tommemod.client.renderer.layer.AccessoryElytraLayer;
import net.finnigan.tommemod.client.renderer.layer.AccessoryHeadLayer;
import net.finnigan.tommemod.client.screen.OvenScreen;
import net.finnigan.tommemod.entity.ModEntityTypes;
import net.finnigan.tommemod.entity.custom.ButterflyEntity;
import net.finnigan.tommemod.entity.custom.EndLanternEntity;
import net.finnigan.tommemod.entity.custom.JellyfishEntity;
import net.finnigan.tommemod.entity.custom.MushlingEntity;
import net.finnigan.tommemod.item.ModItems;
import net.finnigan.tommemod.item.custom.BlossomKatanaItem;
import net.finnigan.tommemod.menu.ModMenuTypes;
import net.finnigan.tommemod.particle.ModParticleTypes;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = "tommemod", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup { // .MOD file, idk im too lazy to research but it doesnt do stuff every tick

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
        event.enqueueWork(() -> {
            ItemProperties.register(
                    ModItems.BLOSSOM_KATANA.get(),
                    new ResourceLocation(TommeMod.MOD_ID, "active"),
                    (stack, level, entity, seed) ->
                            BlossomKatanaItem.isAuraActive(stack, level) ? 1.0F : 0.0F
                );
            });
        }

    @SubscribeEvent
    public static void registerParticleFactories(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticleTypes.AQUATANA_PARTICLE.get(), AquatanaParticle.Provider::new);
        event.registerSpriteSet(ModParticleTypes.FIRE_LARGE_1.get(), FireRingParticle.Provider::new);
        event.registerSpriteSet(ModParticleTypes.FIRE_LARGE_2.get(), FireRingParticle.Provider::new);
        event.registerSpriteSet(ModParticleTypes.FIRE_SMALL_1.get(), FireRingParticle.Provider::new);
        event.registerSpriteSet(ModParticleTypes.FIRE_SMALL_2.get(), FireRingParticle.Provider::new);
        for (var particle : new net.minecraftforge.registries.RegistryObject[]{
                ModParticleTypes.WAVE_1, ModParticleTypes.WAVE_2, ModParticleTypes.WAVE_3, ModParticleTypes.WAVE_4, ModParticleTypes.WAVE_5,
                ModParticleTypes.FOAM_1, ModParticleTypes.FOAM_2, ModParticleTypes.FOAM_3, ModParticleTypes.FOAM_4, ModParticleTypes.FOAM_5}) {
            event.registerSpriteSet((net.minecraft.core.particles.ParticleType) particle.get(), AquatanaParticle.Provider::new);
        }
    }

    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntityTypes.JELLYFISH.get(), JellyfishEntity.createAttributes().build());
        event.put(ModEntityTypes.BUTTERFLY.get(), ButterflyEntity.createAttributes().build());
        event.put(ModEntityTypes.END_LANTERN.get(), EndLanternEntity.createAttributes().build());
        event.put(ModEntityTypes.MUSHLING.get(), MushlingEntity.createAttributes().build());
    }

    @SubscribeEvent
    public static void registerSpawnPlacements(SpawnPlacementRegisterEvent event) {
        event.register(ModEntityTypes.MUSHLING.get(),
                SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                MushlingEntity::checkMushlingSpawnRules,
                SpawnPlacementRegisterEvent.Operation.REPLACE);

        event.register(ModEntityTypes.END_LANTERN.get(),
                SpawnPlacements.Type.NO_RESTRICTIONS,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                EndLanternEntity::checkEndLanternSpawnRules,
                SpawnPlacementRegisterEvent.Operation.REPLACE);
    }

    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (String skinName : event.getSkins()) {
            PlayerRenderer renderer = event.getSkin(skinName);
            if (renderer != null) {
                renderer.addLayer(new AccessoryHeadLayer<>(renderer));
                renderer.addLayer(new AccessoryElytraLayer<>(renderer, event.getEntityModels()));
            }
        }
    }

    @Mod.EventBusSubscriber(modid = TommeMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientEvents {
        @SubscribeEvent
        public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(ModEntityTypes.JELLYFISH.get(), JellyfishRenderer::new);
            event.registerEntityRenderer(ModEntityTypes.BUTTERFLY.get(), ButterflyRenderer::new);
            event.registerEntityRenderer(ModEntityTypes.MUSHLING.get(), MushlingRenderer::new);
            event.registerEntityRenderer(ModEntityTypes.END_LANTERN.get(), EndLanternRenderer::new);
            event.registerEntityRenderer(ModEntityTypes.DYNAMITE.get(), ThrownItemRenderer::new);
            event.registerEntityRenderer(ModEntityTypes.MUSIC_NOTE.get(), MusicNoteRenderer::new);
            event.registerEntityRenderer(ModEntityTypes.GIANT_SWORD.get(), GiantSwordRenderer::new);
            event.registerEntityRenderer(ModEntityTypes.GRAPPLE_HOOK.get(), GrappleHookRenderer::new);

        }
    }
}