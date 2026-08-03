package net.finnigan.tommemod.item.custom.CustodireGladioHelpers;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Custodire Gladio's deployed shield walls, held in a static pending list ticked by
 * ShieldWallTickHandler - the same idiom as War Flammer's FireWaveManager.
 * A wall is a 3-block-wide, 3-block-tall plane standing in front of its caster. It eats projectiles
 * crossing it from either side until its damage budget runs out, and sustains anyone sheltering far
 * enough behind it. Both the budget and the sustain rate scale with the caster's Chief tier
 * (see ChiefTierResolver).
 */
public class ShieldWallManager {

    private static final List<ShieldWall> activeWalls = new ArrayList<>();

    private static final double WIDTH = 3.0;
    private static final double HEIGHT = 3.0;
    private static final double THICKNESS = 0.4;
    private static final double DEPLOY_DISTANCE = 2.0; // blocks in front of the caster

    /** Projectile damage the wall soaks before shattering, before Chief-tier scaling. */
    private static final double BASE_DAMAGE_BUDGET = 20.0;
    /** Charged against the budget by a projectile with no damage value of its own (eggs, pearls, ...). */
    private static final double UNTYPED_PROJECTILE_COST = 2.0;

    /** Not in the spec; chosen shorter than the 12s cooldown so the wall is a window, not a fixture. */
    private static final int LIFETIME_TICKS = 160; // 8s

    private static final double SHELTER_DISTANCE = 2.0; // how far behind the wall counts as sheltered
    private static final int SUSTAIN_INTERVAL_TICKS = 20; // the spec's "per second"

    private static class ShieldWall {
        final ServerLevel level;
        final Player owner;
        final Vec3 center;
        final Vec3 forward; // wall normal, pointing away from the caster
        final Vec3 right;   // along the wall's width
        final AABB bounds;
        final float sustainPerSecond;
        double damageBudget;
        int ticksLeft = LIFETIME_TICKS;
        int ticksUntilSustain = SUSTAIN_INTERVAL_TICKS;

        ShieldWall(ServerLevel level, Player owner, Vec3 center, Vec3 forward, Vec3 right,
                   double damageBudget, float sustainPerSecond) {
            this.level = level;
            this.owner = owner;
            this.center = center;
            this.forward = forward;
            this.right = right;
            this.damageBudget = damageBudget;
            this.sustainPerSecond = sustainPerSecond;
            this.bounds = buildBounds(center, forward, right);
        }
    }

    /** Raises a wall in front of the caster, sized/budgeted for their current Chief tier. */
    public static void deploy(ServerLevel level, Player owner) {
        Vec3 look = owner.getLookAngle();
        Vec3 forward = new Vec3(look.x, 0, look.z).normalize();
        Vec3 right = new Vec3(-forward.z, 0, forward.x);

        Vec3 center = owner.position()
                .add(forward.scale(DEPLOY_DISTANCE))
                .add(0, HEIGHT / 2.0, 0);

        double scale = ChiefTierResolver.scaleFor(owner);
        activeWalls.add(new ShieldWall(level, owner, center, forward, right,
                BASE_DAMAGE_BUDGET * scale, (float) scale));

        level.playSound(null, center.x, center.y, center.z,
                SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.2F, 0.7F);
    }

    /** Called once per server tick from ShieldWallTickHandler. */
    public static void tick() {
        if (activeWalls.isEmpty()) return;

        List<ShieldWall> finished = new ArrayList<>();
        for (ShieldWall wall : activeWalls) {
            blockProjectiles(wall);
            sustainShelteredPlayers(wall);
            drawWall(wall);

            if (--wall.ticksLeft <= 0 || wall.damageBudget <= 0) {
                shatter(wall);
                finished.add(wall);
            }
        }
        activeWalls.removeAll(finished);
    }

    /**
     * Stops anything crossing the wall from either side. Tested against the projectile's swept path
     * (last tick's position to this one's) rather than its current position, so a fast arrow can't
     * tunnel straight through a plane this thin.
     */
    private static void blockProjectiles(ShieldWall wall) {
        List<Projectile> projectiles = wall.level.getEntitiesOfClass(Projectile.class, wall.bounds.inflate(2.0),
                p -> p.isAlive() && p.getOwner() != wall.owner);

        for (Projectile projectile : projectiles) {
            Vec3 to = projectile.position();
            Vec3 from = to.subtract(projectile.getDeltaMovement());
            if (wall.bounds.clip(from, to).isEmpty()) continue;

            wall.damageBudget -= projectileCost(projectile);
            projectile.discard();

            wall.level.sendParticles(ParticleTypes.CRIT, to.x, to.y, to.z, 6, 0.1, 0.1, 0.1, 0.05);
            wall.level.playSound(null, to.x, to.y, to.z,
                    SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.7F, 1.4F);

            if (wall.damageBudget <= 0) return; // spent; the tick loop retires it below
        }
    }

    private static double projectileCost(Projectile projectile) {
        return projectile instanceof AbstractArrow arrow
                ? Math.max(1.0, arrow.getBaseDamage())
                : UNTYPED_PROJECTILE_COST;
    }

    /** Feeds and heals every player standing at least SHELTER_DISTANCE behind the wall. */
    private static void sustainShelteredPlayers(ShieldWall wall) {
        if (--wall.ticksUntilSustain > 0) return;
        wall.ticksUntilSustain = SUSTAIN_INTERVAL_TICKS;

        for (Player player : wall.level.players()) {
            Vec3 offset = player.position().subtract(wall.center);
            double behind = -offset.dot(wall.forward);
            if (behind < SHELTER_DISTANCE) continue;
            if (Math.abs(offset.dot(wall.right)) > WIDTH / 2.0) continue;
            if (Math.abs(offset.y) > HEIGHT) continue;

            player.getFoodData().eat(Math.round(wall.sustainPerSecond), 0.0F);
            player.heal(wall.sustainPerSecond);
        }
    }

    private static void drawWall(ShieldWall wall) {
        for (double w = -WIDTH / 2.0; w <= WIDTH / 2.0; w += 0.5) {
            for (double h = -HEIGHT / 2.0; h <= HEIGHT / 2.0; h += 0.5) {
                Vec3 point = wall.center.add(wall.right.scale(w)).add(0, h, 0);
                wall.level.sendParticles(ParticleTypes.END_ROD, point.x, point.y, point.z, 1, 0, 0, 0, 0);
            }
        }
    }

    private static void shatter(ShieldWall wall) {
        wall.level.sendParticles(ParticleTypes.CRIT,
                wall.center.x, wall.center.y, wall.center.z, 30, WIDTH / 3.0, HEIGHT / 3.0, 0.2, 0.1);
        wall.level.playSound(null, wall.center.x, wall.center.y, wall.center.z,
                SoundEvents.SHIELD_BREAK, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    private static AABB buildBounds(Vec3 center, Vec3 forward, Vec3 right) {
        Vec3 halfWidth = right.scale(WIDTH / 2.0);
        Vec3 halfThickness = forward.scale(THICKNESS / 2.0);

        Vec3 min = center.subtract(halfWidth).subtract(halfThickness).subtract(0, HEIGHT / 2.0, 0);
        Vec3 max = center.add(halfWidth).add(halfThickness).add(0, HEIGHT / 2.0, 0);
        return new AABB(min, max);
    }
}
