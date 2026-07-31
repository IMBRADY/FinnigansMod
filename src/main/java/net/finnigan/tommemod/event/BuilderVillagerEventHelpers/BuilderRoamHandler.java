package net.finnigan.tommemod.event.BuilderVillagerEventHelpers;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.village.ConstructionSiteRegistry;
import net.finnigan.tommemod.villager.ModVillagers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.List;
import java.util.Set;

/**
 * Makes Builder-profession villagers (a plain vanilla Villager with ModVillagers.BUILDER, not a
 * custom entity class - so no registerGoals override is available) roam toward nearby active
 * construction sites and periodically flourish "working" particles while there. Throttled to run
 * every THROTTLE_TICKS, not every tick, and driven off ConstructionSiteRegistry rather than an
 * expensive per-tick block-entity scan.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class BuilderRoamHandler {

    private static final int THROTTLE_TICKS = 30;
    private static final double SEARCH_RADIUS = 32.0D;
    private static final int MAX_BUILDERS_PER_SITE = 4;
    private static final double MOVE_SPEED = 0.5D;
    // Within this distance of the site, a builder is considered "at work" and stops navigating -
    // it just idles there flourishing particles instead of pathing exactly onto the banner block.
    private static final double WORK_RANGE_SQR = 9.0D * 9.0D;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        for (ServerLevel level : server.getAllLevels()) {
            if (level.getGameTime() % THROTTLE_TICKS != 0) continue;

            Set<BlockPos> activeSites = ConstructionSiteRegistry.getActiveSites(level);
            if (activeSites.isEmpty()) continue;

            for (BlockPos sitePos : activeSites) {
                handleSite(level, sitePos);
            }
        }
    }

    private static void handleSite(ServerLevel level, BlockPos sitePos) {
        AABB searchBox = new AABB(sitePos).inflate(SEARCH_RADIUS);
        List<Villager> builders = level.getEntitiesOfClass(Villager.class, searchBox,
                v -> v.isAlive() && v.getVillagerData().getProfession() == ModVillagers.BUILDER.get());
        if (builders.isEmpty()) return;

        double targetX = sitePos.getX() + 0.5D;
        double targetY = sitePos.getY();
        double targetZ = sitePos.getZ() + 0.5D;

        int handled = 0;
        for (Villager builder : builders) {
            if (handled >= MAX_BUILDERS_PER_SITE) break;
            handled++;

            if (builder.blockPosition().distSqr(sitePos) > WORK_RANGE_SQR) {
                builder.getNavigation().moveTo(targetX, targetY, targetZ, MOVE_SPEED);
            } else {
                spawnWorkParticles(level, builder);
            }
        }
    }

    private static void spawnWorkParticles(ServerLevel level, Villager builder) {
        double px = builder.getX();
        double py = builder.getY() + builder.getBbHeight() * 0.5D;
        double pz = builder.getZ();
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, px, py, pz, 3, 0.3D, 0.3D, 0.3D, 0.0D);
    }
}
