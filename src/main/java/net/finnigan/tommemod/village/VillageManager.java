package net.finnigan.tommemod.village;

import net.finnigan.tommemod.config.ModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Resolves an on-demand "village" identity for any position by clustering claimed POIs
 * (beds/job sites/bells, tagged {@code #minecraft:village}) whose link radii overlap.
 * Village splitting (a POI going away un-merging a cluster) is intentionally not handled.
 */
public class VillageManager extends SavedData {

    private static final String DATA_NAME = "tommemod_villages";
    private static final Predicate<Holder<PoiType>> VILLAGE_POI = holder -> holder.is(PoiTypeTags.VILLAGE);
    private static final int MAX_CACHE_ENTRIES = 2000;

    private final Map<BlockPos, UUID> poiIndex = new HashMap<>();
    private final Map<BlockPos, UUID> anchorIndex = new HashMap<>();
    private final Map<UUID, UUID> elderByVillage = new HashMap<>();
    private final Map<UUID, UUID> chiefByVillage = new HashMap<>();
    private final Map<UUID, Integer> farmEfficiencyLevelByVillage = new HashMap<>();
    private final Map<UUID, Integer> healthyWarriorsLevelByVillage = new HashMap<>();
    private final Map<UUID, Integer> villageTierByVillage = new HashMap<>();
    private final Map<UUID, VillageWalls> wallsByVillage = new HashMap<>();
    private final Map<BlockPos, CacheEntry> resolveCache = new HashMap<>();

    private record CacheEntry(Optional<UUID> villageId, long expiresAtTick) {
    }

    public static VillageManager get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(VillageManager::load, VillageManager::new, DATA_NAME);
    }

    public Optional<UUID> resolveVillage(ServerLevel level, BlockPos pos) {
        long now = level.getGameTime();
        CacheEntry cached = resolveCache.get(pos);
        if (cached != null && cached.expiresAtTick() > now) {
            return cached.villageId();
        }

        // A walled village answers for its own ground before any POI is consulted. That is the whole
        // meaning of the walls upgrade: inside them is the village, whether or not a bed happens to
        // be near enough to cluster, and outside them is not, however many POIs sprawl past the gate.
        Optional<UUID> walled = villageWalling(pos);
        if (walled.isPresent()) {
            cacheResult(pos, walled, now);
            return walled;
        }

        int linkRadius = ModConfig.POI_LINK_RADIUS.get();
        int mergeRadius = linkRadius * 2;
        int maxPois = ModConfig.MAX_BFS_POIS.get();
        int maxIterations = ModConfig.MAX_BFS_ITERATIONS.get();
        double maxRadiusSqr = (double) ModConfig.MAX_VILLAGE_RADIUS_BLOCKS.get() * ModConfig.MAX_VILLAGE_RADIUS_BLOCKS.get();

        PoiManager poiManager = level.getPoiManager();
        Optional<BlockPos> nearest = poiManager.getInRange(VILLAGE_POI, pos, linkRadius, PoiManager.Occupancy.ANY)
                .map(PoiRecord::getPos)
                .min(Comparator.comparingDouble(p -> p.distSqr(pos)));

        if (nearest.isEmpty()) {
            cacheResult(pos, Optional.empty(), now);
            return Optional.empty();
        }

        BlockPos start = nearest.get();
        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        visited.add(start);
        queue.add(start);

        Set<UUID> touchedVillageIds = new LinkedHashSet<>();
        int iterations = 0;

        while (!queue.isEmpty()) {
            if (++iterations > maxIterations || visited.size() > maxPois) break;
            BlockPos current = queue.poll();

            UUID existing = poiIndex.get(current);
            if (existing != null) touchedVillageIds.add(existing);

            poiManager.getInRange(VILLAGE_POI, current, mergeRadius, PoiManager.Occupancy.ANY)
                    .map(PoiRecord::getPos)
                    .forEach(neighbor -> {
                        if (neighbor.distSqr(start) <= maxRadiusSqr && visited.add(neighbor)) {
                            queue.add(neighbor);
                        }
                    });
        }

        BlockPos anchor = visited.stream().min(VillageManager::compareAnchor).orElse(start);

        UUID villageId;
        if (touchedVillageIds.isEmpty()) {
            UUID existingForAnchor = anchorIndex.get(anchor);
            villageId = existingForAnchor != null ? existingForAnchor : UUID.randomUUID();
        } else if (touchedVillageIds.size() == 1) {
            villageId = touchedVillageIds.iterator().next();
        } else {
            villageId = mergeVillages(touchedVillageIds);
        }

        for (BlockPos p : visited) {
            poiIndex.put(p, villageId);
        }
        anchorIndex.put(anchor, villageId);
        setDirty();

        cacheResult(pos, Optional.of(villageId), now);
        return Optional.of(villageId);
    }

    /** The village whose walls enclose this position, if any encloses it. */
    private Optional<UUID> villageWalling(BlockPos pos) {
        for (Map.Entry<UUID, VillageWalls> entry : wallsByVillage.entrySet()) {
            if (entry.getValue().contains(pos)) return Optional.of(entry.getKey());
        }
        return Optional.empty();
    }

    /**
     * The ring enclosing this position, if it is inside one. The question anything spawning a wave
     * asks - "am I about to put this inside somebody's walls?" - and the shape it needs to get out of.
     */
    @javax.annotation.Nullable
    public VillageWalls wallsContaining(BlockPos pos) {
        for (VillageWalls walls : wallsByVillage.values()) {
            if (walls.contains(pos)) return walls;
        }
        return null;
    }

    public VillageRegion resolveVillageRegion(ServerLevel level, UUID villageId) {
        // Once a village is walled its region is the ring, full stop - not a circle sized to POIs that
        // may since have sprawled past the gate.
        VillageWalls walls = wallsByVillage.get(villageId);
        if (walls != null && !walls.discCentres().isEmpty()) {
            return new VillageRegion(walls.centre(), walls.radiusFromCentre());
        }

        BlockPos anchor = anchorIndex.entrySet().stream()
                .filter(e -> e.getValue().equals(villageId))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseGet(() -> poiIndex.entrySet().stream()
                        .filter(e -> e.getValue().equals(villageId))
                        .map(Map.Entry::getKey)
                        .min(VillageManager::compareAnchor)
                        .orElse(BlockPos.ZERO));

        double farthest = 0;
        for (Map.Entry<BlockPos, UUID> e : poiIndex.entrySet()) {
            if (e.getValue().equals(villageId)) {
                double d = Math.sqrt(e.getKey().distSqr(anchor));
                if (d > farthest) farthest = d;
            }
        }
        double radius = farthest + ModConfig.POI_LINK_RADIUS.get();
        return new VillageRegion(anchor, Math.min(radius, maxRangeForTier(getVillageTier(villageId))));
    }

    /**
     * How far a village of this tier is allowed to reach. Placeholder numbers - the design has these
     * as "tbd" per tier - so they are deliberately set well above the default POI link radius, where
     * they raise the ceiling for a bigger village rather than clipping any village that exists today.
     */
    public static double maxRangeForTier(int tier) {
        List<? extends Integer> ranges = ModConfig.VILLAGE_TIER_MAX_RANGE_BLOCKS.get();
        if (ranges.isEmpty()) return Double.MAX_VALUE;
        return ranges.get(Math.min(Math.max(tier, 0), ranges.size() - 1));
    }

    /**
     * Whether this UUID still corresponds to a village this manager actually tracks (i.e. has at
     * least one claimed POI mapped to it). Deliberately independent of any particular entity's
     * current position - e.g. a Warrior Villager that's wandered outside its village's bounds still
     * belongs to that village as long as the village itself is still established.
     */
    public boolean isEstablished(UUID villageId) {
        return poiIndex.containsValue(villageId);
    }

    /**
     * All claimed POI positions belonging to this village - the true shape of a village is the
     * union of each POI's small coverage radius (see resolveVillage), not a circle, so this is
     * what anything wanting to draw/reason about a village's actual footprint should use rather
     * than a single anchor+radius approximation.
     */
    public List<BlockPos> getPoiPositions(UUID villageId) {
        List<BlockPos> positions = new ArrayList<>();
        for (Map.Entry<BlockPos, UUID> entry : poiIndex.entrySet()) {
            if (entry.getValue().equals(villageId)) positions.add(entry.getKey());
        }
        return positions;
    }

    /**
     * The claimed POI of this village nearest to {@code from}, or empty if the village has no claimed
     * POIs at all. A village's real footprint is the union of its POIs' coverage radii rather than a
     * circle around one point (see {@link #resolveVillage}), so "how far outside the village is this?"
     * is a question about the nearest POI - {@link #resolveVillageRegion}'s anchor is only meant for
     * sizing a scan box, and is a poor stand-in for the village's position.
     */
    public Optional<BlockPos> nearestPoi(UUID villageId, BlockPos from) {
        BlockPos nearest = null;
        double nearestSqr = Double.MAX_VALUE;
        for (Map.Entry<BlockPos, UUID> entry : poiIndex.entrySet()) {
            if (!entry.getValue().equals(villageId)) continue;
            double d = entry.getKey().distSqr(from);
            if (d < nearestSqr) {
                nearestSqr = d;
                nearest = entry.getKey();
            }
        }
        return Optional.ofNullable(nearest);
    }

    public Optional<UUID> getElder(UUID villageId) {
        return Optional.ofNullable(elderByVillage.get(villageId));
    }

    public boolean tryRegisterElder(UUID villageId, UUID elderUUID) {
        if (elderByVillage.containsKey(villageId)) return false;
        elderByVillage.put(villageId, elderUUID);
        setDirty();
        return true;
    }

    public void unregisterElder(UUID villageId, UUID elderUUID) {
        if (elderByVillage.remove(villageId, elderUUID)) setDirty();
    }

    /**
     * Chief status is tracked here rather than on any particular Elder entity, so it survives that
     * Elder dying/despawning and a replacement being spawned later.
     */
    public Optional<UUID> getChief(UUID villageId) {
        return Optional.ofNullable(chiefByVillage.get(villageId));
    }

    /** Every village this player currently holds the Chief seat of - the reverse of {@link #getChief}. */
    public Set<UUID> getVillagesChiefedBy(UUID playerUUID) {
        Set<UUID> villages = new HashSet<>();
        for (Map.Entry<UUID, UUID> entry : chiefByVillage.entrySet()) {
            if (entry.getValue().equals(playerUUID)) villages.add(entry.getKey());
        }
        return villages;
    }

    public boolean trySetChief(UUID villageId, UUID playerUUID) {
        if (chiefByVillage.containsKey(villageId)) return false;
        chiefByVillage.put(villageId, playerUUID);
        setDirty();
        return true;
    }

    /** Frees up the village's Chief seat, e.g. when the current Chief's reputation falls back to Novice. */
    public void removeChief(UUID villageId) {
        if (chiefByVillage.remove(villageId) != null) setDirty();
    }

    public int getFarmEfficiencyLevel(UUID villageId) {
        return farmEfficiencyLevelByVillage.getOrDefault(villageId, 0);
    }

    public void setFarmEfficiencyLevel(UUID villageId, int level) {
        farmEfficiencyLevelByVillage.put(villageId, level);
        setDirty();
    }

    public int getHealthyWarriorsLevel(UUID villageId) {
        return healthyWarriorsLevelByVillage.getOrDefault(villageId, 0);
    }

    public void setHealthyWarriorsLevel(UUID villageId, int level) {
        healthyWarriorsLevelByVillage.put(villageId, level);
        setDirty();
    }

    /**
     * The village's military tier: what its Armorer, Weaponsmith, Fletcher and Cleric are allowed to
     * arm its Warriors with, and how far it may reach. Every village starts at 0.
     */
    public int getVillageTier(UUID villageId) {
        return villageTierByVillage.getOrDefault(villageId, 0);
    }

    public void setVillageTier(UUID villageId, int tier) {
        villageTierByVillage.put(villageId, tier);
        setDirty();
    }

    @javax.annotation.Nullable
    public VillageWalls getWalls(UUID villageId) {
        return wallsByVillage.get(villageId);
    }

    public boolean hasWalls(UUID villageId) {
        return wallsByVillage.containsKey(villageId);
    }

    public void setWalls(UUID villageId, VillageWalls walls) {
        wallsByVillage.put(villageId, walls);
        // Every cached answer was given under the old boundary; the ring that just went up moves it.
        resolveCache.clear();
        setDirty();
    }

    /**
     * Whether this position counts as part of the village. Walls are the authority once they exist -
     * on the wall and inside it is in, and outside is out no matter what POIs sit there. Unwalled,
     * this is the ordinary POI-cluster question.
     */
    public boolean isInsideVillage(ServerLevel level, UUID villageId, BlockPos pos) {
        VillageWalls walls = wallsByVillage.get(villageId);
        if (walls != null) return walls.contains(pos);
        return resolveVillage(level, pos).filter(villageId::equals).isPresent();
    }

    private UUID mergeVillages(Set<UUID> ids) {
        UUID keep = ids.stream().min(UUID::compareTo).orElseThrow();
        for (UUID discard : ids) {
            if (discard.equals(keep)) continue;
            for (Map.Entry<BlockPos, UUID> e : poiIndex.entrySet()) {
                if (e.getValue().equals(discard)) e.setValue(keep);
            }
            for (Map.Entry<BlockPos, UUID> e : anchorIndex.entrySet()) {
                if (e.getValue().equals(discard)) e.setValue(keep);
            }
            UUID discardElder = elderByVillage.remove(discard);
            if (discardElder != null) elderByVillage.putIfAbsent(keep, discardElder);

            UUID discardChief = chiefByVillage.remove(discard);
            if (discardChief != null) chiefByVillage.putIfAbsent(keep, discardChief);

            Integer discardFarmEfficiency = farmEfficiencyLevelByVillage.remove(discard);
            if (discardFarmEfficiency != null) {
                farmEfficiencyLevelByVillage.merge(keep, discardFarmEfficiency, Math::max);
            }

            Integer discardHealthyWarriors = healthyWarriorsLevelByVillage.remove(discard);
            if (discardHealthyWarriors != null) {
                healthyWarriorsLevelByVillage.merge(keep, discardHealthyWarriors, Math::max);
            }

            Integer discardTier = villageTierByVillage.remove(discard);
            if (discardTier != null) {
                villageTierByVillage.merge(keep, discardTier, Math::max);
            }

            // Two rings that grew into each other are now one village behind two walls. Both are real
            // - they are standing in the world - so the merged village is enclosed by their union.
            VillageWalls discardWalls = wallsByVillage.remove(discard);
            if (discardWalls != null) {
                wallsByVillage.merge(keep, discardWalls, VillageManager::unionWalls);
            }
        }
        setDirty();
        return keep;
    }

    /** Both rings at once, at whichever disc radius was the wider of the two, so neither shrinks. */
    private static VillageWalls unionWalls(VillageWalls a, VillageWalls b) {
        List<BlockPos> centres = new ArrayList<>(a.discCentres());
        centres.addAll(b.discCentres());
        return new VillageWalls(centres, Math.max(a.discRadius(), b.discRadius()));
    }

    private void cacheResult(BlockPos pos, Optional<UUID> result, long now) {
        int ttl = ModConfig.RESOLUTION_CACHE_TTL_TICKS.get();
        if (ttl <= 0) return;
        if (resolveCache.size() > MAX_CACHE_ENTRIES) resolveCache.clear();
        resolveCache.put(pos, new CacheEntry(result, now + ttl));
    }

    private static int compareAnchor(BlockPos a, BlockPos b) {
        if (a.getY() != b.getY()) return Integer.compare(a.getY(), b.getY());
        if (a.getZ() != b.getZ()) return Integer.compare(a.getZ(), b.getZ());
        return Integer.compare(a.getX(), b.getX());
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag poiList = new ListTag();
        poiIndex.forEach((pos, id) -> poiList.add(writePosEntry(pos, id)));
        tag.put("PoiIndex", poiList);

        ListTag anchorList = new ListTag();
        anchorIndex.forEach((pos, id) -> anchorList.add(writePosEntry(pos, id)));
        tag.put("AnchorIndex", anchorList);

        ListTag elderList = new ListTag();
        elderByVillage.forEach((village, elder) -> {
            CompoundTag e = new CompoundTag();
            e.putUUID("Village", village);
            e.putUUID("Elder", elder);
            elderList.add(e);
        });
        tag.put("Elders", elderList);

        ListTag chiefList = new ListTag();
        chiefByVillage.forEach((village, chief) -> {
            CompoundTag e = new CompoundTag();
            e.putUUID("Village", village);
            e.putUUID("Chief", chief);
            chiefList.add(e);
        });
        tag.put("Chiefs", chiefList);

        ListTag farmEfficiencyList = new ListTag();
        farmEfficiencyLevelByVillage.forEach((village, level) -> {
            CompoundTag e = new CompoundTag();
            e.putUUID("Village", village);
            e.putInt("Level", level);
            farmEfficiencyList.add(e);
        });
        tag.put("FarmEfficiency", farmEfficiencyList);

        ListTag healthyWarriorsList = new ListTag();
        healthyWarriorsLevelByVillage.forEach((village, level) -> {
            CompoundTag e = new CompoundTag();
            e.putUUID("Village", village);
            e.putInt("Level", level);
            healthyWarriorsList.add(e);
        });
        tag.put("HealthyWarriors", healthyWarriorsList);

        ListTag tierList = new ListTag();
        villageTierByVillage.forEach((village, tier) -> {
            CompoundTag e = new CompoundTag();
            e.putUUID("Village", village);
            e.putInt("Tier", tier);
            tierList.add(e);
        });
        tag.put("VillageTiers", tierList);

        ListTag wallList = new ListTag();
        wallsByVillage.forEach((village, walls) -> {
            CompoundTag e = walls.save();
            e.putUUID("Village", village);
            wallList.add(e);
        });
        tag.put("Walls", wallList);

        return tag;
    }

    public static VillageManager load(CompoundTag tag) {
        VillageManager mgr = new VillageManager();

        ListTag poiList = tag.getList("PoiIndex", Tag.TAG_COMPOUND);
        for (int i = 0; i < poiList.size(); i++) {
            CompoundTag e = poiList.getCompound(i);
            mgr.poiIndex.put(readPos(e), e.getUUID("Id"));
        }

        ListTag anchorList = tag.getList("AnchorIndex", Tag.TAG_COMPOUND);
        for (int i = 0; i < anchorList.size(); i++) {
            CompoundTag e = anchorList.getCompound(i);
            mgr.anchorIndex.put(readPos(e), e.getUUID("Id"));
        }

        ListTag elderList = tag.getList("Elders", Tag.TAG_COMPOUND);
        for (int i = 0; i < elderList.size(); i++) {
            CompoundTag e = elderList.getCompound(i);
            mgr.elderByVillage.put(e.getUUID("Village"), e.getUUID("Elder"));
        }

        ListTag chiefList = tag.getList("Chiefs", Tag.TAG_COMPOUND);
        for (int i = 0; i < chiefList.size(); i++) {
            CompoundTag e = chiefList.getCompound(i);
            mgr.chiefByVillage.put(e.getUUID("Village"), e.getUUID("Chief"));
        }

        ListTag farmEfficiencyList = tag.getList("FarmEfficiency", Tag.TAG_COMPOUND);
        for (int i = 0; i < farmEfficiencyList.size(); i++) {
            CompoundTag e = farmEfficiencyList.getCompound(i);
            mgr.farmEfficiencyLevelByVillage.put(e.getUUID("Village"), e.getInt("Level"));
        }

        // Absent from saves made before Healthy Warriors existed - reads as an empty list, so every
        // village simply starts at level 0.
        ListTag healthyWarriorsList = tag.getList("HealthyWarriors", Tag.TAG_COMPOUND);
        for (int i = 0; i < healthyWarriorsList.size(); i++) {
            CompoundTag e = healthyWarriorsList.getCompound(i);
            mgr.healthyWarriorsLevelByVillage.put(e.getUUID("Village"), e.getInt("Level"));
        }

        // Both absent from saves made before tiers and walls existed, and both read as empty lists -
        // so every village on an older save simply starts at tier 0 with no walls, which is where a
        // village starts anyway.
        ListTag tierList = tag.getList("VillageTiers", Tag.TAG_COMPOUND);
        for (int i = 0; i < tierList.size(); i++) {
            CompoundTag e = tierList.getCompound(i);
            mgr.villageTierByVillage.put(e.getUUID("Village"), e.getInt("Tier"));
        }

        ListTag wallList = tag.getList("Walls", Tag.TAG_COMPOUND);
        for (int i = 0; i < wallList.size(); i++) {
            CompoundTag e = wallList.getCompound(i);
            mgr.wallsByVillage.put(e.getUUID("Village"), VillageWalls.load(e));
        }

        return mgr;
    }

    private static CompoundTag writePosEntry(BlockPos pos, UUID id) {
        CompoundTag e = new CompoundTag();
        e.putInt("X", pos.getX());
        e.putInt("Y", pos.getY());
        e.putInt("Z", pos.getZ());
        e.putUUID("Id", id);
        return e;
    }

    private static BlockPos readPos(CompoundTag e) {
        return new BlockPos(e.getInt("X"), e.getInt("Y"), e.getInt("Z"));
    }
}
