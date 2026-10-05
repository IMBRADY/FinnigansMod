package net.finnigan.tommemod.event.SquireEventHelpers;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.config.ModConfig;
import net.finnigan.tommemod.entity.custom.WarriorVillagerEntity;
import net.finnigan.tommemod.village.VillageManager;
import net.finnigan.tommemod.village.VillageRegion;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Runs a village's squires: the Armorer, Weaponsmith, Fletcher and Cleric who kit out its Warriors
 * during their working hours.
 *
 * <p>Driven off player ticks like the mod's other village-scoped handlers, because that is where
 * villages are: entities only tick where a player has the chunks loaded, so a village with nobody
 * near it has no squires to run and no Warriors for them to run at. Work is deduplicated per village
 * rather than per player, so a village with four players standing in it does not get four times the
 * squire output.
 *
 * <p>What each squire actually leaves for the Warriors is in {@link SquireDuties}; this file is only about who gets
 * asked, how often, and whether they are on shift.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class SquireTickHandler {

    private static final Map<UUID, Integer> tickCounters = new HashMap<>();
    /** Last game tick each village's squires were run, so several players in one village share a pass. */
    private static final Map<UUID, Long> lastPassByVillage = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        int interval = Math.max(1, ModConfig.SQUIRE_TICK_INTERVAL_TICKS.get());
        int counter = tickCounters.merge(player.getUUID(), 1, Integer::sum);
        if (counter < interval) return;
        tickCounters.put(player.getUUID(), 0);

        VillageManager manager = VillageManager.get(level);
        Optional<UUID> resolved = manager.resolveVillage(level, player.blockPosition());
        if (resolved.isEmpty()) return;
        UUID villageId = resolved.get();

        int tier = manager.getVillageTier(villageId);
        if (tier <= 0) return; // a tier 0 village has no armoury to draw on

        long now = level.getGameTime();
        Long lastPass = lastPassByVillage.get(villageId);
        if (lastPass != null && now - lastPass < interval) return;
        lastPassByVillage.put(villageId, now);

        runSquires(level, manager, villageId, tier);
    }

    private static void runSquires(ServerLevel level, VillageManager manager, UUID villageId, int tier) {
        VillageRegion region = manager.resolveVillageRegion(level, villageId);
        AABB box = new AABB(region.anchor()).inflate(region.radius());

        List<WarriorVillagerEntity> warriors = level.getEntitiesOfClass(WarriorVillagerEntity.class, box,
                warrior -> warrior.isAlive() && villageId.equals(warrior.reconcileVillageId(level, manager)));
        if (warriors.isEmpty()) return;

        List<Villager> squires = level.getEntitiesOfClass(Villager.class, box,
                villager -> villager.isAlive() && isSquireProfession(villager) && isOnShift(villager));
        if (squires.isEmpty()) return;

        for (Villager squire : squires) {
            SquireDuties.serve(level, squire, warriors, tier);
        }
    }

    private static boolean isSquireProfession(Villager villager) {
        VillagerProfession profession = villager.getVillagerData().getProfession();
        return profession == VillagerProfession.ARMORER
                || profession == VillagerProfession.WEAPONSMITH
                || profession == VillagerProfession.FLETCHER
                || profession == VillagerProfession.CLERIC;
    }

    /**
     * Whether this villager is at work right now.
     *
     * <p>Asked of the villager's own brain rather than of the clock. A villager's working hours are
     * already a thing vanilla schedules and adjusts - it knocks off for a raid, sleeps through a
     * thunderstorm, and starts late if it had to walk back from a berry bush - and reading the
     * schedule it is actually following keeps squire hours identical to trading hours, which is what a
     * player watching an Armorer stand at its blast furnace will expect.
     */
    private static boolean isOnShift(Villager villager) {
        return villager.getBrain().isActive(Activity.WORK);
    }
}
