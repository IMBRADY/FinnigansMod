package net.finnigan.tommemod.client;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.skill.Skill;
import net.finnigan.tommemod.skill.SkillTreeManager;
import net.finnigan.tommemod.skill.data.SkillsHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the experience readout in the corner is reading from.
 *
 * The mod never sends "you just earned 4 Agility" anywhere - {@link
 * net.finnigan.tommemod.skill.SkillSync} deliberately coalesces progress and sends the player's
 * current standing twice a second instead, because a packet per award would be a steady stream per
 * player to move a bar nobody is watching. That decision is worth keeping, so rather than adding the
 * packet back for the sake of a HUD, this works the awards out from the standings: keep last sync's
 * level and experience per skill, and whatever moved is what was earned.
 *
 * The cost of that is granularity, and it is the granularity that was wanted anyway. Ten awards
 * inside the same half-second arrive as one number, which is exactly what "stacks higher if you keep
 * earning" describes - a running total per skill, not ten lines racing each other.
 *
 * Levelling is the one case the subtraction cannot do on its own, since experience resets to nothing
 * on the way up and a plain difference would read as a large loss. {@link #gainBetween} walks the
 * curve across however many levels were crossed instead.
 */
public final class SkillXpFeed {

    /** How long a line stays up once it stops being added to. Three seconds. */
    public static final long LIFETIME_TICKS = 60L;

    /** Awards under this are rounding noise off the sync, not something to announce. */
    private static final double MINIMUM_GAIN = 0.05;

    /** One skill's running total, and when it was last added to. */
    public record Entry(ResourceLocation skill, double xp, long lastGainTick) {

        /** 0 the instant it was earned, 1 at the moment it disappears. */
        public float age(long now) {
            return Math.min(1.0F, (float) (now - lastGainTick) / LIFETIME_TICKS);
        }
    }

    private record Standing(int level, double xp) {
    }

    /** Last seen standing per skill. Absent means never seen, which is not the same as zero. */
    private static final Map<ResourceLocation, Standing> LAST_SEEN = new HashMap<>();
    /** Live lines, oldest first, so a new skill appears underneath rather than shoving the rest down. */
    private static final Map<ResourceLocation, Entry> LIVE = new LinkedHashMap<>();

    private SkillXpFeed() {
    }

    /**
     * Called after a progress packet has been written into the capability.
     *
     * The first sync after joining only takes a baseline. A player logging in has "gained" their
     * entire career since the map was last loaded as far as subtraction is concerned, and throwing
     * fourteen lines up on the join tick is not what any of this is for.
     */
    public static void onProgressSynced(SkillsHandler handler) {
        if (!SkillTreeManager.isLoaded()) return;

        long now = gameTime();
        for (Skill skill : SkillTreeManager.skills()) {
            ResourceLocation id = skill.id();
            Standing current = new Standing(handler.level(id), handler.xp(id));
            Standing previous = LAST_SEEN.put(id, current);
            if (previous == null) continue;

            double gained = gainBetween(skill, previous, current);
            if (gained >= MINIMUM_GAIN) add(id, gained, now);
        }
    }

    /**
     * Experience earned between two standings, across however many levels were crossed.
     *
     * A level going backwards is a respec or an operator command rather than something the player
     * did, and reports nothing - there is no such thing as a negative award to show.
     */
    private static double gainBetween(Skill skill, Standing previous, Standing current) {
        if (current.level() < previous.level()) return 0.0;
        if (current.level() == previous.level()) return current.xp() - previous.xp();

        // Finish the level they were on, cross any levels passed through whole, then whatever is
        // showing against the level they landed on.
        double total = Math.max(0.0, skill.xpToAdvance(previous.level()) - previous.xp());
        for (int level = previous.level() + 1; level < current.level(); level++) {
            total += skill.xpToAdvance(level);
        }
        return total + current.xp();
    }

    private static void add(ResourceLocation skill, double gained, long now) {
        Entry existing = LIVE.get(skill);
        // Re-put rather than merge in place: a skill that has gone quiet and come back belongs at the
        // bottom of the list with the other live ones, not wherever it was standing an hour ago.
        if (existing != null && now - existing.lastGainTick() < LIFETIME_TICKS) {
            LIVE.put(skill, new Entry(skill, existing.xp() + gained, now));
        } else {
            LIVE.remove(skill);
            LIVE.put(skill, new Entry(skill, gained, now));
        }
    }

    /** Everything still showing, oldest first. Expiry happens here so nothing has to tick. */
    public static List<Entry> live() {
        if (LIVE.isEmpty()) return List.of();

        long now = gameTime();
        LIVE.values().removeIf(entry -> now - entry.lastGainTick() >= LIFETIME_TICKS);

        return new ArrayList<>(LIVE.values());
    }

    /** Dropped on disconnect: another world's standings are not this world's baseline. */
    public static void reset() {
        LAST_SEEN.clear();
        LIVE.clear();
    }

    private static long gameTime() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? 0L : minecraft.level.getGameTime();
    }

    /**
     * Baselines are per-world. Carrying one across a disconnect would meet the next world's first
     * sync holding numbers from somewhere else and read the whole difference as a single award.
     */
    @Mod.EventBusSubscriber(modid = TommeMod.MOD_ID, value = Dist.CLIENT)
    public static final class Lifecycle {

        private Lifecycle() {
        }

        @SubscribeEvent
        public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            reset();
        }
    }
}
