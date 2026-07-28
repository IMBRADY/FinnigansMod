package net.finnigan.tommemod.event;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.config.ModConfig;
import net.finnigan.tommemod.entity.ModEntityTypes;
import net.finnigan.tommemod.entity.custom.ElderVillagerEntity;
import net.finnigan.tommemod.village.VillageManager;
import net.finnigan.tommemod.village.VillageRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.UUID;

/**
 * A village's Elder dying doesn't leave it leaderless forever - ElderVillagerEntity#die schedules a
 * successor with VillageManager, and once that delay elapses this picks a random unemployed Villager
 * still in the village and promotes it into the new Elder, marked with a (harmless, visual-only)
 * lightning strike. Retries on later checks if no eligible Villager is available yet.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class ElderVillagerSuccessionEvents {

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.level instanceof ServerLevel level)) return;

        int interval = ModConfig.ELDER_SUCCESSION_CHECK_INTERVAL_TICKS.get();
        long now = level.getGameTime();
        if (now % interval != 0) return;

        VillageManager manager = VillageManager.get(level);
        for (UUID villageId : manager.duePendingSuccessions(now)) {
            performSuccession(level, manager, villageId);
        }
    }

    private static void performSuccession(ServerLevel level, VillageManager manager, UUID villageId) {
        if (manager.getElder(villageId).isPresent()) {
            // Already has an Elder again somehow (e.g. manually spawned) - nothing left to do.
            manager.clearPendingSuccession(villageId);
            return;
        }

        VillageRegion region = manager.resolveVillageRegion(level, villageId);
        AABB box = new AABB(region.anchor()).inflate(region.radius());
        List<Villager> candidates = level.getEntitiesOfClass(Villager.class, box, v ->
                v.isAlive() && !v.isBaby() && isUnemployed(v.getVillagerData().getProfession()));
        if (candidates.isEmpty()) return; // no eligible Villager yet - retry on the next check

        Villager chosen = candidates.get(level.random.nextInt(candidates.size()));

        ElderVillagerEntity newElder = ModEntityTypes.ELDER_VILLAGER.get().create(level);
        if (newElder == null) return;

        newElder.copyPosition(chosen);
        newElder.setVillageId(villageId);
        if (!level.addFreshEntity(newElder)) return;

        BlockPos successionPos = chosen.blockPosition();
        chosen.releasePoi(MemoryModuleType.HOME);
        chosen.releasePoi(MemoryModuleType.JOB_SITE);
        chosen.releasePoi(MemoryModuleType.MEETING_POINT);
        chosen.discard();

        manager.tryRegisterElder(villageId, newElder.getUUID());
        manager.clearPendingSuccession(villageId);
        strikeCeremonialLightning(level, successionPos);
    }

    private static boolean isUnemployed(VillagerProfession profession) {
        return profession == VillagerProfession.NONE || profession == VillagerProfession.NITWIT;
    }

    private static void strikeCeremonialLightning(ServerLevel level, BlockPos pos) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) return;

        bolt.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        bolt.setVisualOnly(true); // sound + visual strike only - no fire, no damage, no charged creepers
        level.addFreshEntity(bolt);
    }
}
