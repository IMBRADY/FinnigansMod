package net.finnigan.tommemod.entity.custom;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Reusable texture configuration, steering input, and pendulum/reel physics for grappling weapons. */
public final class GrapplingHookSupport {
    private static final Map<UUID, Float> SWING_INPUT = new HashMap<>();

    private GrapplingHookSupport() {}

    public record Settings(ResourceLocation chainTexture, ResourceLocation tipTexture,
                           double flightSpeed, double maxDistance,
                           double reelSpeed, double reelAcceleration, double reelDeceleration,
                           double brakingDistance, double arrivalDistance,
                           double swingAcceleration, double maxSwingSpeed, double swingDrag,
                           float segmentLength, float chainWidth, float tipScale, float tipRotationDegrees) {}

    public static final class State {
        private double reelSpeed;
        private boolean crouchWasDown;
        private double shortenTargetY;
        private int shortenTicks;
    }

    public static void setSwingInput(Player player, float input) {
        if (Math.abs(input) < 0.001F) SWING_INPUT.remove(player.getUUID());
        else SWING_INPUT.put(player.getUUID(), Math.max(-1.0F, Math.min(1.0F, input)));
    }

    public static float getSwingInput(Player player) {
        return SWING_INPUT.getOrDefault(player.getUUID(), 0.0F);
    }

    public static void pullPlayer(Entity anchor, Player owner, State state, Settings settings, float strafeInput) {
        Vec3 toHook = anchor.position().subtract(owner.getEyePosition());
        double distance = toHook.length();
        if (distance < 1.0E-4) return;
        Vec3 rope = toHook.scale(1.0 / distance);
        Vec3 velocity = owner.getDeltaMovement();
        Vec3 tangent = velocity.subtract(rope.scale(velocity.dot(rope))).scale(settings.swingDrag());

        Vec3 look = owner.getLookAngle();
        Vec3 flatLook = new Vec3(look.x, 0.0, look.z);
        if (flatLook.lengthSqr() > 1.0E-6 && Math.abs(strafeInput) > 0.001F) {
            Vec3 right = new Vec3(-flatLook.z, 0.0, flatLook.x).normalize();
            Vec3 swing = right.subtract(rope.scale(right.dot(rope)));
            if (swing.lengthSqr() > 1.0E-6) {
                tangent = tangent.add(swing.normalize().scale(
                        -Math.signum(strafeInput) * settings.swingAcceleration()));
            }
        }
        if (tangent.length() > settings.maxSwingSpeed()) {
            tangent = tangent.normalize().scale(settings.maxSwingSpeed());
        }

        double factor = Math.max(0.0, Math.min(1.0,
                (distance - settings.arrivalDistance()) / settings.brakingDistance()));
        double target = settings.reelSpeed() * factor;
        double step = state.reelSpeed > target ? settings.reelDeceleration() : settings.reelAcceleration();
        state.reelSpeed = approach(state.reelSpeed, target, step);
        owner.setDeltaMovement(rope.scale(state.reelSpeed).add(tangent));
        owner.hurtMarked = true;
        owner.fallDistance = 0;
    }

    /**
     * On a fresh crouch press, rapidly shortens the effective hanging height by up to two blocks.
     * Holding crouch does not retrigger it; the key must be released and pressed again.
     */
    public static void grappleShorten(Entity anchor, Player owner, State state, boolean crouching) {
        if (crouching && !state.crouchWasDown) {
            state.shortenTargetY = owner.getY() + 2.0;
            state.shortenTicks = 10;
        }
        state.crouchWasDown = crouching;

        if (state.shortenTicks <= 0) return;
        double heightRemaining = state.shortenTargetY - owner.getY();
        if (heightRemaining <= 0.05) {
            state.shortenTicks = 0;
            return;
        }

        Vec3 velocity = owner.getDeltaMovement();
        double upwardSpeed = Math.min(0.42, heightRemaining);
        owner.setDeltaMovement(velocity.x, Math.max(velocity.y, upwardSpeed), velocity.z);
        owner.hurtMarked = true;
        owner.fallDistance = 0;
        state.shortenTicks--;
    }

    private static double approach(double current, double target, double amount) {
        return current < target ? Math.min(target, current + amount) : Math.max(target, current - amount);
    }
}
