package net.finnigan.tommemod.item.custom.AquatanaHelpers;

import net.finnigan.tommemod.item.custom.AquatanaItem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class DashTrailManager {

    private static final List<TrailSegment> activeSegments = new ArrayList<>();

    public static final int TRAIL_LIFETIME_TICKS = 60;   // trail lifetime
    public static final int DAMAGE_INTERVAL_TICKS = 10;  // damage tick every 0.5s per segment
    public static final double SEGMENT_RADIUS = 1.3;
    public static final float TRAIL_DAMAGE = 4.0F;

    private static class TrailSegment {
        final ServerLevel level;
        final Vec3 position;
        final Player owner;
        int age = 0;
        int lastDamageTick = -DAMAGE_INTERVAL_TICKS;

        TrailSegment(ServerLevel level, Vec3 position, Player owner) {
            this.level = level;
            this.position = position;
            this.owner = owner;
        }
    }

    public static void addSegment(ServerLevel level, Vec3 position, Player owner) {
        activeSegments.add(new TrailSegment(level, position, owner));
    }

    /** Called once per server tick from the event handler. */
    public static void tick() {
        if (activeSegments.isEmpty()) return;

        List<TrailSegment> toRemove = new ArrayList<>();

        for (TrailSegment segment : activeSegments) {
            segment.age++;
            if (segment.age >= TRAIL_LIFETIME_TICKS) {
                toRemove.add(segment);
                continue;
            }

            if (segment.age - segment.lastDamageTick >= DAMAGE_INTERVAL_TICKS) {
                segment.lastDamageTick = segment.age;
                damageNearbyEnemies(segment);
            }
        }

        activeSegments.removeAll(toRemove);
    }

    private static void damageNearbyEnemies(TrailSegment segment) {
        AABB box = new AABB(segment.position, segment.position).inflate(SEGMENT_RADIUS);
        List<LivingEntity> nearby = segment.level.getEntitiesOfClass(LivingEntity.class, box,
                AquatanaItem::isValidDashTarget);

        for (LivingEntity target : nearby) {
            DamageSource source = segment.owner.damageSources().playerAttack(segment.owner);
            target.hurt(source, TRAIL_DAMAGE);
        }

        // Visual pulse at the segment, whether or not something was actually hit
        segment.level.sendParticles(
                AquatanaItem.randomAquatanaParticle(segment.level.getRandom()),
                segment.position.x, segment.position.y + 0.1, segment.position.z, 8, 0.3, 0.15, 0.3, 0.05);
    }
}