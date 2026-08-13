package net.finnigan.tommemod.entity.custom.WarriorVillagerHelpers;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The cry that goes up when something attacks one of a village's own, and the only thing a Warrior
 * far from the fight has to go on.
 *
 * Deliberately pushed here by event.WarriorVillagerAlarmEvents when someone is actually hurt, rather
 * than discovered by the Warriors themselves: a call carries three times a Warrior's follow range, and
 * having every Warrior sweep a radius that large every tick looking for a victim would cost far more
 * than it is worth. Being hurt is a rare, already-eventful moment; hanging the search off it is free.
 *
 * One call per village, latest wins. A village under attack in two places at once is a village whose
 * Warriors should converge on whichever blow landed most recently, and it keeps this to a single
 * lookup with nothing to prune.
 */
public final class VillageAlarm {

    /** How long a call stays worth answering before Warriors go back to their business, in ticks. */
    private static final long CALL_DURATION_TICKS = 200;

    /**
     * Who is attacking the village and where the blow landed. The attacker is held directly rather
     * than by UUID so a responder can lock on without a world lookup; calls fall stale within ten
     * seconds, so nothing is held for long.
     */
    public record DistressCall(LivingEntity attacker, BlockPos where, long raisedAt) {

        boolean isLive(long gameTime) {
            return attacker.isAlive() && gameTime - raisedAt <= CALL_DURATION_TICKS;
        }
    }

    private static final Map<UUID, DistressCall> CALLS = new ConcurrentHashMap<>();

    private VillageAlarm() {
    }

    public static void raise(UUID villageId, LivingEntity attacker, BlockPos where, long gameTime) {
        CALLS.put(villageId, new DistressCall(attacker, where, gameTime));
    }

    /** The call this village's Warriors should be answering, or null if there is nothing to answer. */
    @Nullable
    public static DistressCall current(@Nullable UUID villageId, long gameTime) {
        if (villageId == null) return null;

        DistressCall call = CALLS.get(villageId);
        if (call == null) return null;
        if (!call.isLive(gameTime)) {
            CALLS.remove(villageId, call);
            return null;
        }
        return call;
    }

    /** Live state belonging to a running server, not to a save - dropped along with the server. */
    public static void clear() {
        CALLS.clear();
    }
}
