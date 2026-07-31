package net.finnigan.tommemod.village;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight registry of active (not-yet-completed) construction-site banner positions, keyed per
 * level, so things like BuilderRoamHandler can find "the nearest active site" without an expensive
 * per-tick full-level block-entity scan. ConstructionSiteBlockEntity adds itself here on a
 * successful {@code initialize(...)} and removes itself on completion or demolition. Keyed with a
 * WeakHashMap so unloaded/unregistered levels (e.g. on server shutdown between tests) don't leak.
 */
public class ConstructionSiteRegistry {

    private static final Map<ServerLevel, Set<BlockPos>> ACTIVE_SITES = new WeakHashMap<>();

    private ConstructionSiteRegistry() {
    }

    public static synchronized void register(ServerLevel level, BlockPos pos) {
        ACTIVE_SITES.computeIfAbsent(level, l -> ConcurrentHashMap.newKeySet()).add(pos.immutable());
    }

    public static synchronized void unregister(ServerLevel level, BlockPos pos) {
        Set<BlockPos> sites = ACTIVE_SITES.get(level);
        if (sites != null) sites.remove(pos);
    }

    /** Snapshot of currently-active site positions for this level - safe to iterate without holding
     * any lock on the registry itself. */
    public static synchronized Set<BlockPos> getActiveSites(ServerLevel level) {
        Set<BlockPos> sites = ACTIVE_SITES.get(level);
        return sites != null ? Set.copyOf(sites) : Collections.emptySet();
    }
}
