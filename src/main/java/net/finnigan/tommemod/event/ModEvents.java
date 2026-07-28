package net.finnigan.tommemod.event;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.client.JellyfishRenderer;
import net.finnigan.tommemod.client.renderer.layer.AccessoryElytraLayer;
import net.finnigan.tommemod.client.renderer.layer.AccessoryHeadLayer;
import net.finnigan.tommemod.entity.ModEntityTypes;
import net.finnigan.tommemod.entity.custom.*;
import net.finnigan.tommemod.entity.custom.Bosses.BossCrab.BossCrabEntity;
import net.finnigan.tommemod.item.custom.LongbowItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "tommemod")
public class ModEvents {

    @SubscribeEvent
    public static void onArrowSpawn(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return; // only touch server-side entity

        if (event.getEntity() instanceof AbstractArrow arrow
                && arrow.getOwner() instanceof Player player) {
            ItemStack bow = player.getUseItem();
            if (bow.getItem() instanceof LongbowItem) {
                arrow.setBaseDamage(arrow.getBaseDamage() * 2.0); // Damage
                arrow.setDeltaMovement(arrow.getDeltaMovement().scale(2.0)); // Speed
            }
        }
    }

    // Zombies/husks/drowned and raiders (Pillager, Vindicator, Evoker, Ravager...) come with vanilla
    // targeting for Villager/IronGolem baked into their own registerGoals() - since WarriorVillagerEntity
    // isn't either of those types, they'd otherwise walk right past it. Bolt on the same targeting the
    // instant one of these mobs joins the world (targetSelector is public on Forge's Mob), so it's
    // treated like any other villager-defender for raid/zombie AI purposes.
    @SubscribeEvent
    public static void onHostileMobSpawn(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;

        if (event.getEntity() instanceof Monster monster && (monster instanceof Zombie || monster instanceof Raider)) {
            monster.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(monster, WarriorVillagerEntity.class, true));
        }
    }

    // Target-selection alone (see WarriorVillagerEntity.registerGoals) already stops a Warrior from
    // intentionally attacking a Villager/ElderVillager/other Warrior/IronGolem, but it can't stop
    // incidental friendly fire - an arrow, crossbow bolt, or Musket shot aimed at a real enemy can still
    // physically clip a bystander standing in the flight path (IronGolems in particular wander right
    // through a fight). DamageSource#getEntity() credits the shooter for arrows/bolts and the mob itself
    // for the Musket's instant-hit shot, so this one check catches all three weapon types.
    @SubscribeEvent
    public static void onWarriorFriendlyFire(LivingAttackEvent event) {
        if (!(event.getSource().getEntity() instanceof WarriorVillagerEntity attacker)) return;

        LivingEntity victim = event.getEntity();
        if (victim == attacker) return;

        if (victim instanceof WarriorVillagerEntity warriorVictim) {
            if (attacker.getVillageId() != null && attacker.getVillageId().equals(warriorVictim.getVillageId())) {
                event.setCanceled(true);
            }
        } else if (victim instanceof Villager || victim instanceof ElderVillagerEntity || victim instanceof IronGolem) {
            event.setCanceled(true);
        }
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
        event.register(ModEntityTypes.BIRDIE.get(),
                SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                BirdieEntity::checkBirdieSpawnRules,
                SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(ModEntityTypes.SEAGULL.get(),
                SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                SeagullEntity::checkSeagullSpawnRules,
                SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(ModEntityTypes.DUNGEON_CRAB.get(),
                SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.WORLD_SURFACE,
                DungeonCrabEntity::checkDungeonCrabSpawnRules,
                SpawnPlacementRegisterEvent.Operation.REPLACE);
        SpawnPlacements.register(ModEntityTypes.LIVING_ARMOR.get(),
                SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                LivingArmorEntity::checkLivingArmorSpawnRules);
        event.register(ModEntityTypes.DUCK.get(),
                SpawnPlacements.Type.IN_WATER,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                DuckEntity::checkDuckSpawnRules,
                SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(ModEntityTypes.CRAB.get(),
                SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                CrabEntity::checkCrabSpawnRules,
                SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(ModEntityTypes.HERMIT_CRAB.get(),
                SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                HermitCrabEntity::checkHermitCrabSpawnRules,
                SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(ModEntityTypes.SCARECROW.get(),
                SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                ScarecrowEntity::checkScarecrowSpawnRules,
                SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(ModEntityTypes.CYCLOPS.get(),
                SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                CyclopsEntity::checkCyclopsSpawnRules,
                SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(ModEntityTypes.HAMMERHEAD_SHARK.get(),
                SpawnPlacements.Type.IN_WATER,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                HammerheadSharkEntity::checkHammerheadSpawnRules,
                SpawnPlacementRegisterEvent.Operation.REPLACE);
    }
}
