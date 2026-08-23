package net.finnigan.tommemod.entity.ai;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

/**
 * Shared flight physics for the mod's flyers.
 *
 * <p>Every navigation-driven flyer here used to override {@code travel()} with the same three lines:
 * {@code moveRelative(getSpeed(), travelVector)}, move, then scale the velocity by 0.91. That treats
 * the move control's speed - a cruising speed in blocks per tick - as a per-tick <em>acceleration</em>,
 * and 0.91 drag turns an acceleration of {@code a} into a top speed of {@code a / (1 - 0.91)}, about
 * eleven times larger. The Wyvern's FLYING_SPEED of 0.8 therefore settled near nine blocks per tick
 * (~180 blocks/second), and the insects were not much better. They all overshot their navigation
 * waypoints and jittered back towards them, which is what made the movement look broken.
 *
 * <p>{@link #travel} scales the acceleration by {@code 1 - DRAG} instead, so a mob's
 * {@link net.minecraft.world.entity.ai.attributes.Attributes#FLYING_SPEED} multiplied by its goal's
 * speed modifier is simply its cruising speed in blocks per tick.
 */
public final class FlightPhysics {

    /** Fraction of its velocity an airborne entity keeps each tick - the vanilla air drag figure. */
    public static final double DRAG = 0.91D;

    /** How much horizontal momentum a descending flyer keeps per tick, so it glides down rather than stopping dead. */
    private static final double DESCENT_HORIZONTAL_BLEED = 0.6D;

    private FlightPhysics() {
    }

    /**
     * The body of {@code travel()} for a mob flown by a
     * {@link net.minecraft.world.entity.ai.control.FlyingMoveControl}. Callers still run their own
     * {@code calculateEntityAnimation} afterwards, which is protected and so cannot live here.
     */
    public static void travel(Mob mob, Vec3 travelVector) {
        if (!mob.isControlledByLocalInstance()) return;

        // A unit heading keeps the acceleration magnitude exactly what we asked for. The raw vector the
        // move control produces is (0, +/-speed, speed), so its length would otherwise scale with the
        // speed a second time.
        Vec3 heading = travelVector.lengthSqr() > 1.0E-7D ? travelVector.normalize() : Vec3.ZERO;
        mob.moveRelative((float) (mob.getSpeed() * (1.0D - DRAG)), heading);
        mob.move(MoverType.SELF, mob.getDeltaMovement());
        mob.setDeltaMovement(mob.getDeltaMovement().scale(DRAG));
    }

    /**
     * One tick of a controlled descent, for the bird goals that fly on raw velocity. Returns true once
     * the bird is actually resting on ground or water.
     *
     * <p>Those goals used to switch their flying flag off the moment their flight timer ran out,
     * wherever they happened to be. That cuts the bird's lift in mid-air and drops it - the duck falling
     * out of the sky. Coming down under power and only clearing the flag on contact avoids that.
     *
     * <p>Only the velocity is set here; the mob's own {@code travel()} does the move, so the descent
     * still collides with terrain normally.
     */
    public static boolean descend(Mob mob, double speed) {
        if (mob.onGround() || mob.isInWater()) return true;

        Vec3 momentum = mob.getDeltaMovement();
        mob.setDeltaMovement(momentum.x * DESCENT_HORIZONTAL_BLEED, -speed, momentum.z * DESCENT_HORIZONTAL_BLEED);
        return false;
    }
}
