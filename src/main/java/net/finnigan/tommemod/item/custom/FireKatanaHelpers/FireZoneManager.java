package net.finnigan.tommemod.item.custom.FireKatanaHelpers;

import net.finnigan.tommemod.particle.ModParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;

public class FireZoneManager {

    private static final List<FireZone> activeZones = new ArrayList<>();

    public static final int ZONE_LIFETIME_TICKS = 120; // duration of zone
    private static final int PARTICLE_INTERVAL_TICKS = 4;

    @SuppressWarnings("unchecked")
    private static final RegistryObject<SimpleParticleType>[] RING_PARTICLES = new RegistryObject[]{
            ModParticleTypes.FIRE_LARGE_1,
            ModParticleTypes.FIRE_LARGE_2,
            ModParticleTypes.FIRE_SMALL_1,
            ModParticleTypes.FIRE_SMALL_2
    };

    private static class FireZone {
        final ServerLevel level;
        final double centerX, centerY, centerZ;
        final double radius;
        final Player owner;
        int age = 0;

        FireZone(ServerLevel level, double x, double y, double z, double radius, Player owner) {
            this.level = level;
            this.centerX = x;
            this.centerY = y;
            this.centerZ = z;
            this.radius = radius;
            this.owner = owner;
        }
    }

    public static void addZone(ServerLevel level, double x, double y, double z, double radius, Player owner) {
        activeZones.add(new FireZone(level, x, y, z, radius, owner));
    }

    /** Called once per server tick. */
    public static void tick() {
        if (activeZones.isEmpty()) return;

        List<FireZone> toRemove = new ArrayList<>();

        for (FireZone zone : activeZones) {
            zone.age++;
            if (zone.age >= ZONE_LIFETIME_TICKS) {
                toRemove.add(zone);
                continue;
            }

            if (zone.age % PARTICLE_INTERVAL_TICKS == 0) {
                spawnFillParticles(zone);
            }

            applyDoubleBurnDamage(zone);
        }

        activeZones.removeAll(toRemove);
    }

    /** Checks if a given position is inside any active fire zone (used for the damage-doubling hook). */
    public static boolean isInAnyFireZone(LivingEntity entity) {
        for (FireZone zone : activeZones) {
            double dx = entity.getX() - zone.centerX;
            double dz = entity.getZ() - zone.centerZ;
            if (dx * dx + dz * dz <= zone.radius * zone.radius) {
                return true;
            }
        }
        return false;
    }

    private static void applyDoubleBurnDamage(FireZone zone) {
        AABB box = new AABB(zone.centerX - zone.radius, zone.centerY - 2, zone.centerZ - zone.radius,
                zone.centerX + zone.radius, zone.centerY + 2, zone.centerZ + zone.radius);

        List<LivingEntity> nearby = zone.level.getEntitiesOfClass(LivingEntity.class, box,
                net.finnigan.tommemod.item.custom.FireKatanaItem::isValidFireTarget);

        for (LivingEntity target : nearby) {
            double dx = target.getX() - zone.centerX;
            double dz = target.getZ() - zone.centerZ;
            if (dx * dx + dz * dz > zone.radius * zone.radius) continue;

            // Refresh fire so it doesn't expire while standing in the zone
            if (target.isOnFire()) {
                target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), 20));
            }
        }
    }

    private static void spawnFillParticles(FireZone zone) {
        // # particles
        int fillPoints = 50;
        for (int i = 0; i < fillPoints; i++) {
            double angle = zone.level.getRandom().nextDouble() * 2 * Math.PI;
            double dist = zone.level.getRandom().nextDouble() * zone.radius;
            double x = zone.centerX + dist * Math.cos(angle);
            double z = zone.centerZ + dist * Math.sin(angle);

            RegistryObject<SimpleParticleType> chosen = RING_PARTICLES[zone.level.getRandom().nextInt(RING_PARTICLES.length)];
            zone.level.sendParticles(chosen.get(), x, zone.centerY + 0.1, z, 1, 0.05, 0.15, 0.05, 0.01);
        }
    }
}