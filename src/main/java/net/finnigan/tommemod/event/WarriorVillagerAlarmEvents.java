package net.finnigan.tommemod.event;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.entity.custom.ElderVillagerEntity;
import net.finnigan.tommemod.entity.custom.WarriorVillagerEntity;
import net.finnigan.tommemod.entity.custom.WarriorVillagerHelpers.VillageAlarm;
import net.finnigan.tommemod.village.VillageManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Raises the alarm when something attacks one of a village's own, so that Warriors too far away to
 * have witnessed it still come running. What they do about it is
 * entity.custom.WarriorVillagerHelpers.AnswerDistressCallGoal and AidAllyTargetGoal.
 *
 * <p>A villager doesn't have to be hit to call for help: one in vanilla's panic - running from a
 * zombie or a raider it has spotted - calls out about whatever it is running from, every second
 * for as long as the chase lasts.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class WarriorVillagerAlarmEvents {

    private static final int PANIC_CALL_INTERVAL_TICKS = 20;

    @SubscribeEvent
    public static void onVillageMemberHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level)) return;
        if (!isVillageMember(victim)) return;

        // getEntity rather than getDirectEntity, so an arrow answers for whoever loosed it.
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) return;
        if (!attacker.isAlive() || isVillageMember(attacker) || attacker instanceof IronGolem) return;

        UUID villageId = villageOf(level, victim);
        if (villageId == null) return; // hurt out in the wilds - there is no village to call to

        VillageAlarm.raise(villageId, attacker, victim.blockPosition(), level.getGameTime());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        if (server == null || server.getTickCount() % PANIC_CALL_INTERVAL_TICKS != 0) return;

        for (ServerLevel level : server.getAllLevels()) {
            for (Villager villager : level.getEntities(EntityTypeTest.forClass(Villager.class),
                    v -> v.isAlive() && v.getBrain().isActive(Activity.PANIC))) {
                LivingEntity chaser = villager.getBrain().getMemory(MemoryModuleType.NEAREST_HOSTILE)
                        .or(() -> villager.getBrain().getMemory(MemoryModuleType.HURT_BY_ENTITY))
                        .orElse(null);
                if (chaser == null || !chaser.isAlive() || isVillageMember(chaser) || chaser instanceof IronGolem) continue;

                UUID villageId = villageOf(level, villager);
                if (villageId != null) VillageAlarm.raise(villageId, chaser, villager.blockPosition(), level.getGameTime());
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        VillageAlarm.clear();
    }

    private static boolean isVillageMember(LivingEntity entity) {
        return entity instanceof Villager
                || entity instanceof ElderVillagerEntity
                || entity instanceof WarriorVillagerEntity;
    }

    /**
     * A Warrior answers for the village it was conscripted into even if it has wandered out of it,
     * which is what lets one calling for help from beyond the walls still be heard. Anyone else is
     * placed by where they are standing.
     */
    @Nullable
    private static UUID villageOf(ServerLevel level, LivingEntity victim) {
        if (victim instanceof WarriorVillagerEntity warrior && warrior.getVillageId() != null) {
            return warrior.getVillageId();
        }
        return VillageManager.get(level).resolveVillage(level, victim.blockPosition()).orElse(null);
    }
}
