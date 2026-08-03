package net.finnigan.tommemod.item.custom.CandeliereHelpers;

import net.finnigan.tommemod.item.custom.FireKatanaItem;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Candeliere's three firework-like flares. There is deliberately no projectile entity: per spec these
 * are purely a trail of flame particles that ends in a flame burst, so each flare is just a moving
 * point advanced once per server tick, drawing particles and testing what it passes through - the same
 * "static pending list ticked by a separate handler" idiom as War Flammer's FireWaveManager.
 */
public class CandeliereFlareManager {

    private static final List<Flare> activeFlares = new ArrayList<>();

    private static final int FLARE_COUNT = 3;
    private static final double SPEED = 0.9;           // blocks per tick
    private static final int MAX_LIFETIME_TICKS = 60;  // ~54 blocks of travel before fizzling out
    private static final double HIT_RADIUS = 1.2;
    private static final double SPREAD = 0.16;         // lateral fan so the three flares don't overlap

    private static final float EXPLOSION_DAMAGE = 5.0F;
    private static final double EXPLOSION_RADIUS = 2.0;
    public static final int BASE_BURN_SECONDS = 5;

    private static class Flare {
        final ServerLevel level;
        final Player owner;
        Vec3 position;
        final Vec3 direction;
        int ticksAlive = 0;

        Flare(ServerLevel level, Player owner, Vec3 position, Vec3 direction) {
            this.level = level;
            this.owner = owner;
            this.position = position;
            this.direction = direction;
        }
    }

    /** Fires the three-flare volley from the player's current eye position and facing. */
    public static void fireVolley(ServerLevel level, Player owner) {
        Vec3 origin = owner.getEyePosition();
        Vec3 look = owner.getLookAngle().normalize();

        // Any vector perpendicular to the look direction works as the fan axis; the horizontal one
        // reads best, and look is never vertical enough for this to degenerate in practice.
        Vec3 side = new Vec3(-look.z, 0, look.x);
        if (side.lengthSqr() < 1.0E-6) side = new Vec3(1, 0, 0);
        side = side.normalize();

        for (int i = 0; i < FLARE_COUNT; i++) {
            Vec3 direction = look.add(side.scale((i - 1) * SPREAD)).normalize();
            activeFlares.add(new Flare(level, owner, origin, direction));
        }
    }

    /** Called once per server tick from CandeliereFlareTickHandler. */
    public static void tick() {
        if (activeFlares.isEmpty()) return;

        List<Flare> finished = new ArrayList<>();
        for (Flare flare : activeFlares) {
            if (advance(flare)) finished.add(flare);
        }
        activeFlares.removeAll(finished);
    }

    /** Advances one flare a single step. Returns true once the flare is spent. */
    private static boolean advance(Flare flare) {
        Vec3 from = flare.position;
        Vec3 to = from.add(flare.direction.scale(SPEED));

        HitResult blockHit = flare.level.clip(new ClipContext(
                from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, flare.owner));
        if (blockHit.getType() == HitResult.Type.BLOCK && blockHit instanceof BlockHitResult hit) {
            explode(flare, hit.getLocation());
            return true;
        }

        flare.position = to;
        spawnTrail(flare);

        LivingEntity struck = firstTargetAt(flare);
        if (struck != null) {
            explode(flare, flare.position);
            return true;
        }

        return ++flare.ticksAlive >= MAX_LIFETIME_TICKS;
    }

    private static LivingEntity firstTargetAt(Flare flare) {
        AABB box = new AABB(flare.position, flare.position).inflate(HIT_RADIUS);
        List<LivingEntity> hits = flare.level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e.isAlive() && e != flare.owner && FireKatanaItem.isValidFireTarget(e));
        return hits.isEmpty() ? null : hits.get(0);
    }

    private static void spawnTrail(Flare flare) {
        flare.level.sendParticles(ParticleTypes.FLAME,
                flare.position.x, flare.position.y, flare.position.z, 3, 0.05, 0.05, 0.05, 0.005);
        flare.level.sendParticles(ParticleTypes.SMALL_FLAME,
                flare.position.x, flare.position.y, flare.position.z, 2, 0.08, 0.08, 0.08, 0.0);
    }

    private static void explode(Flare flare, Vec3 at) {
        flare.level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 60, 0.4, 0.4, 0.4, 0.08);
        flare.level.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, 8, 0.3, 0.3, 0.3, 0.0);
        flare.level.playSound(null, at.x, at.y, at.z,
                SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 1.7F, 4.0F);

        AABB box = new AABB(at, at).inflate(EXPLOSION_RADIUS);
        List<LivingEntity> targets = flare.level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e.isAlive() && e != flare.owner && FireKatanaItem.isValidFireTarget(e));

        for (LivingEntity target : targets) {
            if (target.position().distanceTo(at) > EXPLOSION_RADIUS) continue;
            target.hurt(flare.owner.damageSources().playerAttack(flare.owner), EXPLOSION_DAMAGE);
            CandeliereBurnTracker.igniteFresh(target, flare.owner, BASE_BURN_SECONDS);
        }
    }
}
