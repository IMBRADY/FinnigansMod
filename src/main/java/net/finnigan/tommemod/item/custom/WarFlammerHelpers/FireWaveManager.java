package net.finnigan.tommemod.item.custom.WarFlammerHelpers;

import net.finnigan.tommemod.particle.ModParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Static pending-wave list for War Flammer's "Fire Wave" ability, ticked externally by
 * FireWaveTickHandler (same "static list ticked by a separate handler" idiom as FireZoneManager).
 * Each entry represents one in-flight wave: the player who cast it, the origin/facing captured at
 * cast time, and how far along its 4 scheduled steps it is.
 */
public class FireWaveManager {

    private static final List<PendingWave> activeWaves = new ArrayList<>();

    private static final int STEP_COUNT = 4;
    private static final int TICKS_BETWEEN_STEPS = 4;

    // AOE proc damage per wave-step hit. Deliberately lower than the base 25 left-click hit: this is a
    // secondary multi-hit ability that can strike several enemies across up to 4 steps (and a single
    // enemy could conceivably be caught by more than one step), not a single decisive attack, so it is
    // tuned as a strong-but-not-primary proc rather than matching the base swing damage.
    private static final float STEP_DAMAGE = 25.0F;

    private static final int IGNITE_SECONDS = 5;
    private static final double KNOCKUP_STRENGTH = 1.2; // vanilla knockback() has no vertical component
    private static final double VERTICAL_HALF_HEIGHT = 2.0;

    private static class PendingWave {
        final ServerLevel level;
        final Player owner;
        final Vec3 origin;
        final Vec3 forward;
        final Vec3 right;
        int stepIndex = 0;
        int ticksUntilNextStep = 0;

        PendingWave(ServerLevel level, Player owner, Vec3 origin, Vec3 forward, Vec3 right) {
            this.level = level;
            this.owner = owner;
            this.origin = origin;
            this.forward = forward;
            this.right = right;
        }
    }

    /** Kicks off a new 4-step fire wave from the player's current position/facing. */
    public static void startWave(ServerLevel level, Player owner) {
        Vec3 origin = owner.position();
        Vec3 look = owner.getLookAngle();
        Vec3 forward = new Vec3(look.x, 0, look.z).normalize();
        Vec3 right = new Vec3(-forward.z, 0, forward.x); // perpendicular in the horizontal plane

        activeWaves.add(new PendingWave(level, owner, origin, forward, right));
    }

    /** Called once per server tick from FireWaveTickHandler. */
    public static void tick() {
        if (activeWaves.isEmpty()) return;

        List<PendingWave> finished = new ArrayList<>();
        for (PendingWave wave : activeWaves) {
            if (wave.ticksUntilNextStep > 0) {
                wave.ticksUntilNextStep--;
                continue;
            }

            performStep(wave);
            wave.stepIndex++;
            wave.ticksUntilNextStep = TICKS_BETWEEN_STEPS;

            if (wave.stepIndex >= STEP_COUNT) {
                finished.add(wave);
            }
        }
        activeWaves.removeAll(finished);
    }

    /** Step i covers the forward band [2*i, 2*(i+1)) blocks, with half-width (1+i) blocks. */
    private static void performStep(PendingWave wave) {
        int step = wave.stepIndex;
        double near = 2.0 * step;
        double far = 2.0 * (step + 1);
        double halfWidth = 1.0 + step;

        AABB bounds = new AABB(
                wave.origin.x - far, wave.origin.y - VERTICAL_HALF_HEIGHT, wave.origin.z - far,
                wave.origin.x + far, wave.origin.y + VERTICAL_HALF_HEIGHT, wave.origin.z + far);

        List<LivingEntity> targets = wave.level.getEntitiesOfClass(LivingEntity.class, bounds,
                e -> e.isAlive() && e != wave.owner);

        for (LivingEntity target : targets) {
            Vec3 offset = target.position().subtract(wave.origin);
            double forwardDist = offset.dot(wave.forward);
            double lateralDist = offset.dot(wave.right);

            if (forwardDist < near || forwardDist >= far) continue;
            if (Math.abs(lateralDist) > halfWidth) continue;

            DamageSource source = wave.owner.damageSources().playerAttack(wave.owner);
            target.hurt(source, STEP_DAMAGE);
            target.setSecondsOnFire(IGNITE_SECONDS);

            Vec3 vel = target.getDeltaMovement();
            target.setDeltaMovement(vel.x, KNOCKUP_STRENGTH, vel.z);
            target.hurtMarked = true;
        }

        spawnStepParticles(wave, near, far, halfWidth);

        wave.level.playSound(null, wave.owner.getX(), wave.owner.getY(), wave.owner.getZ(),
                SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 0.8F, 1.0F + step * 0.1F);
    }

    private static void spawnStepParticles(PendingWave wave, double near, double far, double halfWidth) {
        int points = 12 + wave.stepIndex * 4;
        for (int i = 0; i < points; i++) {
            double forwardDist = near + wave.level.getRandom().nextDouble() * (far - near);
            double lateralDist = (wave.level.getRandom().nextDouble() * 2.0 - 1.0) * halfWidth;

            Vec3 pos = wave.origin
                    .add(wave.forward.scale(forwardDist))
                    .add(wave.right.scale(lateralDist));

            SimpleParticleType particle = (i % 2 == 0)
                    ? ModParticleTypes.WAR_FLAMMER_WAVE_1.get()
                    : ModParticleTypes.WAR_FLAMMER_WAVE_2.get();

            wave.level.sendParticles(particle, pos.x, wave.origin.y + 0.2, pos.z, 1, 0.1, 0.15, 0.1, 0.01);
        }
    }
}
