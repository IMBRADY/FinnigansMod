package net.finnigan.tommemod.item.custom.BlossomKatanaHelpers;

import net.finnigan.tommemod.entity.custom.UndeadSwordHelpers.SoulSummoner;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.finnigan.tommemod.entity.ModEntityTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.List;

public class BlossomAuraZone {

    // Refresh buff duration — must outlast the tick gap between refreshes,
    // but be short enough that leaving the zone drops the buff quickly.
    private static final int BUFF_REFRESH_DURATION = 30; // 1.5 seconds

    public final ServerLevel level;
    public final double centerX;
    public final double centerY;
    public final double centerZ;
    public final double radius;
    private int ticksRemaining;

    public BlossomAuraZone(ServerLevel level, double x, double y, double z, double radius, int durationTicks) {
        this.level = level;
        this.centerX = x;
        this.centerY = y;
        this.centerZ = z;
        this.radius = radius;
        this.ticksRemaining = durationTicks;
    }

    /** @return true if the zone is finished and should be removed */
    public boolean tick() {
        if (ticksRemaining <= 0) {
            return true;
        }
        ticksRemaining--;

        buffEntitiesInRadius();
        spawnParticles();

        return false;
    }

    private void buffEntitiesInRadius() {
        AABB searchBox = new AABB(
                centerX - radius, centerY - radius, centerZ - radius,
                centerX + radius, centerY + radius, centerZ + radius
        );
        double radiusSq = radius * radius;

        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, searchBox,
                entity -> entity.distanceToSqr(centerX, centerY, centerZ) <= radiusSq && isValidTarget(entity));

        for (LivingEntity target : targets) {
            // Only re-add if the current remaining duration is below the refresh
            // threshold, so we're not spamming addEffect every single tick.
            MobEffectInstance currentRegen = target.getEffect(MobEffects.REGENERATION);
            if (currentRegen == null || currentRegen.getDuration() < BUFF_REFRESH_DURATION) {
                target.addEffect(new MobEffectInstance(MobEffects.REGENERATION, BUFF_REFRESH_DURATION, 1, false, true, true));
            }

            MobEffectInstance currentSpeed = target.getEffect(MobEffects.MOVEMENT_SPEED);
            if (currentSpeed == null || currentSpeed.getDuration() < BUFF_REFRESH_DURATION) {
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, BUFF_REFRESH_DURATION, 1, false, true, true));
            }
        }
    }

    private boolean isValidTarget(LivingEntity entity) {
        if (entity instanceof TamableAnimal tamable) {
            return tamable.isTame();
        }
        return entity instanceof Player
                || entity instanceof Villager
                || entity.getTags().contains(SoulSummoner.SOUL_ALLY_TAG);
    }

    private void spawnParticles() {
        for (int i = 0; i < 3; i++) {
            double angle = level.random.nextDouble() * Math.PI * 2;
            double dist = level.random.nextDouble() * radius;
            double x = centerX + Math.cos(angle) * dist;
            double z = centerZ + Math.sin(angle) * dist;

            level.sendParticles(ParticleTypes.CHERRY_LEAVES, x, centerY + 0.5, z, 1, 0.05, 0.15, 0.05, 0.01);
        }
    }
}