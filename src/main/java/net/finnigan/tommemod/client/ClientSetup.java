package net.finnigan.tommemod.client;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.client.particle.AquatanaParticle;
import net.finnigan.tommemod.client.particle.FireRingParticle;
import net.finnigan.tommemod.client.renderer.*;
import net.finnigan.tommemod.client.renderer.layer.AccessoryElytraLayer;
import net.finnigan.tommemod.client.renderer.layer.AccessoryHeadLayer;
import net.finnigan.tommemod.client.screen.OvenScreen;
import net.finnigan.tommemod.entity.ModEntityTypes;
import net.finnigan.tommemod.entity.custom.*;
import net.finnigan.tommemod.entity.custom.Bosses.BossCrab.BossCrabEntity;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
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
            ItemProperties.register(Items.ENCHANTED_BOOK, new ResourceLocation(TommeMod.MOD_ID, "enchant_type"),
                    (stack, level, entity, seed) -> {
                        if (stack.isEmpty()) return 0.0F;
                        net.minecraft.nbt.CompoundTag tag = stack.getTag();
                        if (tag != null) {
                            String listKey = tag.contains("StoredEnchantments", 9) ? "StoredEnchantments" : "Enchantments";
                            net.minecraft.nbt.ListTag list = tag.getList(listKey, 10);
                            for (int i = 0; i < list.size(); i++) {
                                net.minecraft.nbt.CompoundTag enchant = list.getCompound(i);
                                String id = enchant.getString("id");

                                // Assign a unique number to each enchantment type
                                if ("minecraft:bane_of_arthropods".equals(id)) {
                                    return 1.0F;
                                }
                                if ("minecraft:aqua_affinity".equals(id)) {
                                    return 2.0F;
                                }
                                if ("minecraft:blast_protection".equals(id)) {
                                    return 3.0F;
                                }
                                if ("minecraft:channeling".equals(id)) {
                                    return 4.0F;
                                }
                                if ("minecraft:curse_of_binding".equals(id)) {
                                    return 5.0F;
                                }
                                if ("minecraft:curse_of_vanishing".equals(id)) {
                                    return 6.0F;
                                }
                                if ("minecraft:depth_strider".equals(id)) {
                                    return 7.0F;
                                }
                                if ("minecraft:efficiency".equals(id)) {
                                    return 8.0F;
                                }
                                if ("minecraft:feather_falling".equals(id)) {
                                    return 9.0F;
                                }
                                if ("minecraft:fire_aspect".equals(id)) {
                                    return 10.0F;
                                }
                                if ("minecraft:fire_protection".equals(id)) {
                                    return 11.0F;
                                }
                                if ("minecraft:flame".equals(id)) {
                                    return 12.0F;
                                }
                                if ("minecraft:fortune".equals(id)) {
                                    return 13.0F;
                                }
                                if ("minecraft:frost_walker".equals(id)) {
                                    return 14.0F;
                                }
                                if ("minecraft:glowing".equals(id)) {
                                    return 15.0F;
                                }
                                if ("minecraft:impailing".equals(id)) {
                                    return 16.0F;
                                }
                                if ("minecraft:infinity".equals(id)) {
                                    return 17.0F;
                                }
                                if ("minecraft:knockback".equals(id)) {
                                    return 18.0F;
                                }
                                if ("minecraft:looting".equals(id)) {
                                    return 19.0F;
                                }
                                if ("minecraft:loyalty".equals(id)) {
                                    return 20.0F;
                                }
                                if ("minecraft:luck_of_the_sea".equals(id)) {
                                    return 21.0F;
                                }
                                if ("minecraft:lure".equals(id)) {
                                    return 22.0F;
                                }
                                if ("minecraft:mending".equals(id)) {
                                    return 23.0F;
                                }
                                if ("minecraft:multishot".equals(id)) {
                                    return 24.0F;
                                }
                                if ("minecraft:piercing".equals(id)) {
                                    return 25.0F;
                                }
                                if ("minecraft:power".equals(id)) {
                                    return 26.0F;
                                }
                                if ("minecraft:projectile_protection".equals(id)) {
                                    return 27.0F;
                                }
                                if ("minecraft:protection".equals(id)) {
                                    return 28.0F;
                                }
                                if ("minecraft:punch".equals(id)) {
                                    return 29.0F;
                                }
                                if ("minecraft:quick_charge".equals(id)) {
                                    return 30.0F;
                                }
                                if ("minecraft:respiration".equals(id)) {
                                    return 31.0F;
                                }
                                if ("minecraft:riptide".equals(id)) {
                                    return 32.0F;
                                }
                                if ("minecraft:sharpness".equals(id)) {
                                    return 33.0F;
                                }
                                if ("minecraft:silk_touch".equals(id)) {
                                    return 34.0F;
                                }
                                if ("minecraft:smite".equals(id)) {
                                    return 35.0F;
                                }
                                if ("minecraft:soul_speed".equals(id)) {
                                    return 36.0F;
                                }
                                if ("minecraft:sweeping_edge".equals(id)) {
                                    return 37.0F;
                                }
                                if ("minecraft:thorns".equals(id)) {
                                    return 38.0F;
                                }
                                if ("minecraft:unbreaking".equals(id)) {
                                    return 39.0F;
                                }
                            }
                        }
                        return 0.0F;
                    });
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

    // IMPORTANT
    // FOR MULTIPLAYER SERVERS MOB SPAWNS MUST GO IN HERE OR ELSE THEY WILL NOT SPAWN!!!

    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntityTypes.JELLYFISH.get(), JellyfishEntity.createAttributes().build());
        event.put(ModEntityTypes.BUTTERFLY.get(), ButterflyEntity.createAttributes().build());
        event.put(ModEntityTypes.END_LANTERN.get(), EndLanternEntity.createAttributes().build());
        event.put(ModEntityTypes.MUSHLING.get(), MushlingEntity.createAttributes().build());
        event.put(ModEntityTypes.BOSS_CRAB.get(), BossCrabEntity.createAttributes().build());
        event.put(ModEntityTypes.CAPYBARA.get(), CapybaraEntity.createAttributes().build());
        event.put(ModEntityTypes.MANTA.get(), MantaEntity.createAttributes().build());
        event.put(ModEntityTypes.TIGER.get(), TigerEntity.createAttributes().build());
        event.put(ModEntityTypes.BIRDIE.get(), BirdieEntity.createAttributes().build());
    }

    @SubscribeEvent
    public static void registerSpawnPlacements(SpawnPlacementRegisterEvent event) {
        event.register(ModEntityTypes.MUSHLING.get(),
                SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                MushlingEntity::checkMushlingSpawnRules,
                SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(ModEntityTypes.CAPYBARA.get(),
                SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                CapybaraEntity::checkCapybaraSpawnRules,
                SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(ModEntityTypes.END_LANTERN.get(),
                SpawnPlacements.Type.NO_RESTRICTIONS,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                EndLanternEntity::checkEndLanternSpawnRules,
                SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(ModEntityTypes.MANTA.get(),
                SpawnPlacements.Type.IN_WATER,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                MantaEntity::checkMantaSpawnRules,
                SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(ModEntityTypes.JELLYFISH.get(),
                SpawnPlacements.Type.IN_WATER,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                JellyfishEntity::checkSurfaceWaterAnimalSpawnRules,
                SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(ModEntityTypes.TIGER.get(),
                SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                TigerEntity::checkTigerSpawnRules,
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
            event.registerEntityRenderer(ModEntityTypes.BOSS_CRAB.get(), BossCrabRenderer::new);
            event.registerEntityRenderer(ModEntityTypes.CAPYBARA.get(), CapybaraRenderer::new);
            event.registerEntityRenderer(ModEntityTypes.MANTA.get(), MantaRenderer::new);
            event.registerEntityRenderer(ModEntityTypes.TIGER.get(), TigerRenderer::new);
            event.registerEntityRenderer(ModEntityTypes.BIRDIE.get(), BirdieRenderer::new);
        }
    }
}