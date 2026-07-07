package net.finnigan.tommemod.event;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

@Mod.EventBusSubscriber(modid = "tommemod", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ChainLightningHandler {

    private static final double CHAIN_RADIUS = 12.0;
    private static final int MICRO_PAUSE_TICKS = 4;
    private static final float CHAIN_DAMAGE = 20F;

    private static final Set<UUID> chainSpawnedBoltIds = new HashSet<>();
    private static final List<PendingHop> pendingHops = new ArrayList<>();

    /**
     * Public entry point — call this from anything that wants to kick off a chain,
     * e.g. LightningRodSwordItem after its beam lands.
     *
     * @param origin      world position the chain starts searching from
     * @param alreadyHit  UUIDs to exclude from the very first hop (e.g. entities the direct beam already hit)
     * @param maxHops     how many jumps this particular chain is allowed to make
     */
    public static void startChain(ServerLevel level, Vec3 origin, Set<UUID> alreadyHit, int maxHops) {
        queueNextHop(level, origin, alreadyHit, maxHops);
    }

    private static void queueNextHop(ServerLevel level, Vec3 fromPos, Set<UUID> alreadyHit, int hopsRemaining) {
        if (hopsRemaining <= 0) return;

        LivingEntity target = findNearestValidTarget(level, fromPos, alreadyHit);
        if (target == null) return;

        pendingHops.add(new PendingHop(level, fromPos, target.getUUID(), alreadyHit, hopsRemaining, MICRO_PAUSE_TICKS));
    }



    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (pendingHops.isEmpty()) return;

        List<PendingHop> readyToFire = new ArrayList<>();

        Iterator<PendingHop> iterator = pendingHops.iterator();
        while (iterator.hasNext()) {
            PendingHop hop = iterator.next();
            hop.delayRemaining--;

            if (hop.delayRemaining <= 0) {
                readyToFire.add(hop);
                iterator.remove();
            }
        }

        // Execute AFTER the iteration/removal loop is fully done, so any new hops
        // queued here (via executeHop -> queueNextHop -> pendingHops.add) can't
        // collide with the iterator above.
        for (PendingHop hop : readyToFire) {
            executeHop(hop);
        }
    }

    private static void executeHop(PendingHop hop) {
        Entity targetEntity = hop.level.getEntity(hop.targetUUID);
        if (!(targetEntity instanceof LivingEntity target) || !target.isAlive()) return;

        spawnElectricTrail(hop.level, hop.fromPos, target.position());

        target.hurt(hop.level.damageSources().lightningBolt(), CHAIN_DAMAGE);
        hop.alreadyHit.add(target.getUUID());

        queueNextHop(hop.level, target.position(), hop.alreadyHit, hop.hopsRemaining - 1);
    }

    private static void spawnElectricTrail(ServerLevel level, Vec3 startPos, Vec3 endPos) {
        double totalDistance = startPos.distanceTo(endPos);
        int particleCount = Math.max(6, (int) (totalDistance * 4));

        Vec3 direction = endPos.subtract(startPos).normalize();
        Vec3 arbitrary = Math.abs(direction.y) < 0.99 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 perpendicularA = direction.cross(arbitrary).normalize();
        Vec3 perpendicularB = direction.cross(perpendicularA).normalize();

        double jitterAmount = 0.15;

        for (int i = 0; i <= particleCount; i++) {
            double t = (double) i / particleCount;
            Vec3 pointOnLine = startPos.lerp(endPos, t);

            double jitterA = (level.random.nextDouble() - 0.5) * jitterAmount;
            double jitterB = (level.random.nextDouble() - 0.5) * jitterAmount;

            Vec3 jitteredPoint = pointOnLine
                    .add(perpendicularA.scale(jitterA))
                    .add(perpendicularB.scale(jitterB));

            level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                    jitteredPoint.x, jitteredPoint.y, jitteredPoint.z,
                    1, 0, 0, 0, 0);
        }
    }

    private static LivingEntity findNearestValidTarget(ServerLevel level, Vec3 origin, Set<UUID> excluded) {
        AABB searchBox = new AABB(
                origin.x - CHAIN_RADIUS, origin.y - CHAIN_RADIUS, origin.z - CHAIN_RADIUS,
                origin.x + CHAIN_RADIUS, origin.y + CHAIN_RADIUS, origin.z + CHAIN_RADIUS);

        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, searchBox,
                e -> e.isAlive()
                        && !excluded.contains(e.getUUID())
                        && !(e instanceof Player)
                        && !(e instanceof Villager)
                        && !(e instanceof Wolf));

        LivingEntity closest = null;
        double closestDistSq = CHAIN_RADIUS * CHAIN_RADIUS;

        for (LivingEntity candidate : candidates) {
            double distSq = candidate.position().distanceToSqr(origin);
            if (distSq < closestDistSq) {
                closestDistSq = distSq;
                closest = candidate;
            }
        }
        return closest;
    }

    private static class PendingHop {
        final ServerLevel level;
        final Vec3 fromPos;
        final UUID targetUUID;
        final Set<UUID> alreadyHit;
        final int hopsRemaining;
        int delayRemaining;

        PendingHop(ServerLevel level, Vec3 fromPos, UUID targetUUID, Set<UUID> alreadyHit, int hopsRemaining, int delayRemaining) {
            this.level = level;
            this.fromPos = fromPos;
            this.targetUUID = targetUUID;
            this.alreadyHit = alreadyHit;
            this.hopsRemaining = hopsRemaining;
            this.delayRemaining = delayRemaining;
        }
    }
}