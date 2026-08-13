package net.finnigan.tommemod.event.ShadowSwordHelpers;

import net.finnigan.tommemod.entity.custom.ShadowSwordHelpers.ShadowSoulEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * All of the Shadow Sword's soul bookkeeping: how many souls a wielder is carrying, the passive that
 * feeds them, the orbit they're drawn in, and the staggered volley a right-click fires.
 *
 * Souls are deliberately server-side-only transient state rather than NBT on the sword - they only
 * exist while the sword is actually held, and putting it away drops them (see
 * {@link #tickPlayer(Player)}). The orbit is drawn with server-sent particles for the same reason:
 * nothing about a soul count needs to survive a relog, so nothing needs syncing.
 */
public class ShadowSoulManager {

    public static final int MAX_SOULS = 6;
    /** Marker for a volley that can't earn its souls back (i.e. wasn't thrown at max). */
    public static final int NO_REFUND_TOKEN = -1;

    private static final int PASSIVE_INTERVAL_TICKS = 60; // one soul every 3 seconds
    private static final int VOLLEY_DELAY_TICKS = 4;      // 0.2s between souls
    private static final int VOLLEY_DELAY_TICKS_AT_MAX = 2; // halved at max souls
    /** How long a full-power volley stays eligible for its refund, in ticks. Just needs to outlast
     * the souls themselves, which expire at the end of their range. */
    private static final int REFUND_LIFETIME_TICKS = 200;

    private static final double ORBIT_RADIUS = 0.9;
    private static final double ORBIT_HEIGHT = 1.0;

    private static final Map<UUID, Integer> souls = new HashMap<>();
    private static final Map<UUID, Integer> passiveProgress = new HashMap<>();
    private static final Map<UUID, Volley> volleys = new HashMap<>();
    private static final Map<Integer, PendingRefund> refunds = new HashMap<>();

    private static int nextRefundToken = 0;

    /** Souls still queued to launch from one right-click, staggered rather than fired all at once. */
    private static final class Volley {
        private final Vec3 direction;
        private final int delayTicks;
        private final int token;
        private int remaining;
        private int ticksUntilNext;

        private Volley(Vec3 direction, int remaining, int delayTicks, int token) {
            this.direction = direction;
            this.remaining = remaining;
            this.delayTicks = delayTicks;
            this.token = token;
            this.ticksUntilNext = delayTicks;
        }
    }

    private static final class PendingRefund {
        private final UUID owner;
        private final long expiresAtGameTime;

        private PendingRefund(UUID owner, long expiresAtGameTime) {
            this.owner = owner;
            this.expiresAtGameTime = expiresAtGameTime;
        }
    }

    public static int getSouls(Player player) {
        return souls.getOrDefault(player.getUUID(), 0);
    }

    /** Adds a soul if there's room. Returns whether one was actually gained. */
    public static boolean addSoul(Player player) {
        int current = getSouls(player);
        if (current >= MAX_SOULS) return false;
        souls.put(player.getUUID(), current + 1);
        return true;
    }

    public static void forget(Player player) {
        UUID id = player.getUUID();
        souls.remove(id);
        passiveProgress.remove(id);
        volleys.remove(id);
    }

    /**
     * Per-tick upkeep for one player: accrue the passive soul, hold Resistance while at max, draw
     * the orbit, and launch whatever is left of an in-flight volley.
     */
    public static void tickPlayer(Player player) {
        UUID id = player.getUUID();

        if (!ShadowSwordEvents.isWieldingShadowSword(player)) {
            souls.remove(id);
            passiveProgress.remove(id);
            // A volley already in the air keeps going - those souls have left the player's hands.
        } else {
            int progress = passiveProgress.merge(id, 1, Integer::sum);
            if (progress >= PASSIVE_INTERVAL_TICKS) {
                passiveProgress.put(id, 0);
                addSoul(player);
            }

            int count = getSouls(player);
            if (count >= MAX_SOULS) {
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 0, true, false));
            }
            drawOrbit(player, count);
        }

        tickVolley(player);
        expireRefunds(player.level().getGameTime());
    }

    /**
     * Throws everything the player is carrying. Returns false (and does nothing) with no souls.
     *
     * A volley thrown at max souls is the strong version: half the stagger between souls, and a
     * single refund of the whole set if any of them kills something.
     */
    public static boolean throwSouls(Player player) {
        int count = getSouls(player);
        if (count <= 0) return false;

        boolean atMax = count >= MAX_SOULS;
        int delay = atMax ? VOLLEY_DELAY_TICKS_AT_MAX : VOLLEY_DELAY_TICKS;
        int token = NO_REFUND_TOKEN;
        if (atMax) {
            token = nextRefundToken++;
            refunds.put(token, new PendingRefund(player.getUUID(),
                    player.level().getGameTime() + REFUND_LIFETIME_TICKS));
        }

        souls.put(player.getUUID(), 0);

        Vec3 direction = player.getLookAngle().normalize();
        spawnSoul(player, direction, token); // first one leaves immediately, the rest trail behind
        if (count > 1) {
            volleys.put(player.getUUID(), new Volley(direction, count - 1, delay, token));
        }

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1.0F, 0.8F);
        return true;
    }

    /**
     * A thrown soul killed something. Refunds the whole set once per volley - the token is consumed
     * here, so the other five souls of the same throw can't each buy another refund.
     */
    public static void reportSoulKill(int token, @Nullable Player owner) {
        if (token == NO_REFUND_TOKEN) return;
        PendingRefund refund = refunds.remove(token);
        if (refund == null) return;

        souls.put(refund.owner, MAX_SOULS);
        if (owner != null && owner.getUUID().equals(refund.owner)) {
            owner.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("The souls return to you.")
                            .withStyle(style -> style.withColor(0x4FC3C7)), true);
        }
    }

    private static void tickVolley(Player player) {
        Volley volley = volleys.get(player.getUUID());
        if (volley == null) return;

        if (--volley.ticksUntilNext > 0) return;

        spawnSoul(player, volley.direction, volley.token);
        volley.remaining--;
        if (volley.remaining <= 0) {
            volleys.remove(player.getUUID());
        } else {
            volley.ticksUntilNext = volley.delayTicks;
        }
    }

    private static void spawnSoul(Player player, Vec3 direction, int token) {
        ShadowSoulEntity soul = new ShadowSoulEntity(player.level(), player, direction, token);
        player.level().addFreshEntity(soul);
    }

    private static void drawOrbit(Player player, int count) {
        if (count <= 0) return;
        if (!(player.level() instanceof ServerLevel serverLevel)) return;

        double spin = player.tickCount * 0.12;
        double bob = Math.sin(player.tickCount * 0.1) * 0.1;
        for (int i = 0; i < count; i++) {
            double angle = spin + (i * 2.0 * Math.PI / count);
            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    player.getX() + Math.cos(angle) * ORBIT_RADIUS,
                    player.getY() + ORBIT_HEIGHT + bob,
                    player.getZ() + Math.sin(angle) * ORBIT_RADIUS,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private static void expireRefunds(long gameTime) {
        if (refunds.isEmpty()) return;
        Iterator<PendingRefund> it = refunds.values().iterator();
        while (it.hasNext()) {
            if (gameTime >= it.next().expiresAtGameTime) it.remove();
        }
    }
}
