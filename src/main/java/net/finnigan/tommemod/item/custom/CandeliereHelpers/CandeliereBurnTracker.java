package net.finnigan.tommemod.item.custom.CandeliereHelpers;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Bookkeeping for Candeliere's "melee hits stoke the fire" passive: a flare's ignition is what starts
 * a burn, and each subsequent melee hit with the weapon lengthens that burn by a further 10% of its
 * original duration, up to +50%. The stack count is per-burn, so re-igniting a target with a fresh
 * flare hands them a clean slate rather than letting extensions accumulate forever.
 * Keyed weakly so a dead/unloaded target's entry disappears with it instead of leaking.
 */
public class CandeliereBurnTracker {

    private static final int MAX_STACKS = 5;          // 5 x 10% = the +50% ceiling
    private static final double EXTENSION_PER_STACK = 0.10;

    private static final Map<LivingEntity, BurnState> BURNS = new WeakHashMap<>();

    private static class BurnState {
        final int baseTicks;
        int stacks = 0;

        BurnState(int baseTicks) {
            this.baseTicks = baseTicks;
        }
    }

    private CandeliereBurnTracker() {
    }

    /** Sets a target alight from a flare hit, resetting any extensions a previous burn had earned. */
    public static void igniteFresh(LivingEntity target, Player owner, int seconds) {
        target.setSecondsOnFire(seconds);
        BURNS.put(target, new BurnState(seconds * 20));
    }

    /**
     * Lengthens an ability-lit burn by one 10% step. No-op for a target that isn't currently carrying
     * a flare-started fire, so ordinary fire (lava, flint and steel, another weapon) can't be stoked.
     */
    public static void extendOnMelee(LivingEntity target) {
        BurnState state = BURNS.get(target);
        if (state == null) return;

        if (!target.isOnFire() || target.getRemainingFireTicks() <= 0) {
            BURNS.remove(target); // that burn is over; the next flare starts a new one
            return;
        }
        if (state.stacks >= MAX_STACKS) return;

        state.stacks++;
        int added = (int) Math.round(state.baseTicks * EXTENSION_PER_STACK);
        target.setRemainingFireTicks(target.getRemainingFireTicks() + added);
    }
}
