package net.finnigan.tommemod.skill;

import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

/**
 * A number that grows as a node is ranked up.
 *
 * Written one of two ways in a tree file, whichever reads better for the node:
 *
 * <pre>
 *   "amount_per_rank": 0.05      each rank adds 5%, so rank 3 is +15%
 *   "amounts": [0.10, 0.05, 0.05] the first rank is worth more than the two after it
 * </pre>
 *
 * Either way the entries are <em>increments</em>, and {@link #at} returns the running total at a
 * rank - so a node's effect is always read as "what this node is worth right now", never as a
 * per-rank delta the caller has to sum itself.
 */
public record RankedValue(double perRank, @Nullable double[] increments) {

    public static final RankedValue ZERO = new RankedValue(0.0, null);

    /** The total this value contributes at the given rank. Rank 0 (unpurchased) is always zero. */
    public double at(int rank) {
        if (rank <= 0) return 0.0;
        if (increments == null) return perRank * rank;

        double total = 0.0;
        for (int i = 0; i < Math.min(rank, increments.length); i++) {
            total += increments[i];
        }
        // Ranks past the end of an explicit list keep earning the last listed increment, so a node
        // whose max_rank is later raised doesn't silently stop paying out.
        if (rank > increments.length && increments.length > 0) {
            total += increments[increments.length - 1] * (rank - increments.length);
        }
        return total;
    }

    public static RankedValue parse(JsonObject json, String perRankKey, String incrementsKey) {
        if (json.has(incrementsKey)) {
            var array = GsonHelper.getAsJsonArray(json, incrementsKey);
            double[] increments = new double[array.size()];
            for (int i = 0; i < array.size(); i++) {
                increments[i] = array.get(i).getAsDouble();
            }
            return new RankedValue(0.0, increments);
        }
        return new RankedValue(GsonHelper.getAsDouble(json, perRankKey, 0.0), null);
    }

    /** Parses the conventional pair of keys, {@code amount_per_rank} / {@code amounts}. */
    public static RankedValue parseAmount(JsonObject json) {
        return parse(json, "amount_per_rank", "amounts");
    }
}
