package net.finnigan.tommemod.village.buildings;

import net.finnigan.tommemod.village.VillageManager;
import net.finnigan.tommemod.village.blueprint.Blueprint;
import net.finnigan.tommemod.village.blueprint.Blueprints;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every building the Builders have finished in one dimension, and which village it belongs to.
 *
 * <p>A building's purpose only counts while it is still standing. "Standing" is judged against its
 * own blueprint: if most of its blocks are still the blocks the design calls for, it stands. Knock
 * an Observatory down and the map shrinks back; burn the Bank and its vault is out of reach until
 * it is rebuilt. The answer is cached briefly, because it means reading every block of the building.
 *
 * <p>A building's purpose is read from its blueprint as the blueprint is now, not as it was when the
 * building went up - so a building finished before its design was given a job starts doing that job.
 * Each building also keeps what was paid for it and what stood on its ground before, so the Chief can
 * knock it down again, get the materials back and have the ground put back as it was.
 */
public class VillageBuildings extends SavedData {

    private static final String DATA_NAME = "tommemod_buildings";
    /** Share of a building's blocks that must still match its blueprint for it to count. */
    private static final double STANDING_THRESHOLD = 0.6;
    private static final long STANDING_CACHE_TICKS = 200;

    /** One Warrior's bunk in a Barracks: the bed, the chest beside it, and who sleeps there. */
    public static final class BarracksSlot {
        public final BlockPos bedHead;
        public final BlockPos bedFoot;
        @Nullable
        public final BlockPos chest;
        /** Start of this bunk's sleep shift, in ticks into the day. Staggered across a barracks. */
        public final int shiftStart;
        @Nullable
        public UUID warrior;
        /** Game time this bunk's Warrior died, or -1 if it is alive (or has never been spawned). */
        public long diedAt = -1;

        BarracksSlot(BlockPos bedHead, BlockPos bedFoot, @Nullable BlockPos chest, int shiftStart) {
            this.bedHead = bedHead;
            this.bedFoot = bedFoot;
            this.chest = chest;
            this.shiftStart = shiftStart;
        }
    }

    public static final class Building {
        public final UUID id;
        public UUID villageId;
        public final String blueprintId;
        public final String purpose;
        public final Rotation rotation;
        public final BlockPos origin;
        public final BoundingBox bounds;
        public final List<BarracksSlot> slots = new ArrayList<>();
        /** What was paid for it, or null when that isn't known (recorded before payments were, or found by a survey). */
        @Nullable
        public List<Blueprint.Cost> paid;
        /** What stood on its ground before it was built, oldest change first. Empty when not known. */
        public final LinkedHashMap<BlockPos, BlockState> originals = new LinkedHashMap<>();
        private boolean standing = true;
        private long standingCheckedAt = Long.MIN_VALUE;

        Building(UUID id, UUID villageId, String blueprintId, String savedPurpose, Rotation rotation, BlockPos origin, BoundingBox bounds) {
            this.id = id;
            this.villageId = villageId;
            this.blueprintId = blueprintId;
            // The design's purpose as it is today wins over whatever was saved with the building.
            Blueprint bp = Blueprints.get(blueprintId);
            this.purpose = bp != null ? bp.purpose() : savedPurpose;
            this.rotation = rotation;
            this.origin = origin;
            this.bounds = bounds;
        }

        @Nullable
        public Blueprint blueprint() {
            return Blueprints.get(blueprintId);
        }

        public String displayName() {
            Blueprint bp = blueprint();
            return bp != null ? bp.name() : blueprintId;
        }

        public BlockPos centre() {
            return new BlockPos(bounds.getCenter().getX(), bounds.minY(), bounds.getCenter().getZ());
        }

        public boolean contains(BlockPos pos) {
            return bounds.isInside(pos);
        }
    }

    private final Map<UUID, Building> buildings = new LinkedHashMap<>();

    public static VillageBuildings get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(tag -> load(level, tag), VillageBuildings::new, DATA_NAME);
    }

    public Building record(UUID villageId, Blueprint bp, Rotation rotation, BlockPos origin, BoundingBox bounds) {
        Building b = new Building(UUID.randomUUID(), villageId, bp.id(), bp.purpose(), rotation, origin, bounds);
        buildings.put(b.id, b);
        setDirty();
        return b;
    }

    @Nullable
    public Building remove(UUID id) {
        Building removed = buildings.remove(id);
        if (removed != null) setDirty();
        return removed;
    }

    /** Whether any recorded building's footprint overlaps this box. */
    public boolean overlapsAny(BoundingBox box) {
        for (Building b : buildings.values()) {
            if (b.bounds.intersects(box)) return true;
        }
        return false;
    }

    public Collection<Building> all() {
        return buildings.values();
    }

    @Nullable
    public Building get(UUID id) {
        return buildings.get(id);
    }

    /**
     * This village's buildings. A building remembers the village it was built for, but villages can
     * merge and change id, so one whose recorded village has gone is re-homed to wherever it stands.
     */
    public List<Building> inVillage(ServerLevel level, UUID villageId) {
        VillageManager manager = VillageManager.get(level);
        List<Building> out = new ArrayList<>();
        for (Building b : buildings.values()) {
            if (!b.villageId.equals(villageId) && !manager.isEstablished(b.villageId)) {
                manager.resolveVillage(level, b.centre()).ifPresent(v -> {
                    b.villageId = v;
                    setDirty();
                });
            }
            if (b.villageId.equals(villageId)) out.add(b);
        }
        return out;
    }

    public List<Building> standing(ServerLevel level, UUID villageId, String purpose) {
        List<Building> out = new ArrayList<>();
        for (Building b : inVillage(level, villageId)) {
            if (b.purpose.equals(purpose) && isStanding(level, b)) out.add(b);
        }
        return out;
    }

    public int countStanding(ServerLevel level, UUID villageId, String purpose) {
        return standing(level, villageId, purpose).size();
    }

    public boolean hasBank(ServerLevel level, UUID villageId) {
        return countStanding(level, villageId, BuildingPurpose.BANK) > 0;
    }

    /** The standing building of this purpose that contains {@code pos}, if any. */
    @Nullable
    public Building standingAt(ServerLevel level, BlockPos pos, String purpose) {
        for (Building b : buildings.values()) {
            if (b.purpose.equals(purpose) && b.contains(pos) && isStanding(level, b)) return b;
        }
        return null;
    }

    /** Whether enough of this building is left to count. Out of loaded range, the last answer stands. */
    public boolean isStanding(ServerLevel level, Building b) {
        long now = level.getGameTime();
        if (now - b.standingCheckedAt < STANDING_CACHE_TICKS) return b.standing;
        if (!level.isLoaded(b.centre())) return b.standing;
        b.standingCheckedAt = now;

        Blueprint bp = Blueprints.get(b.blueprintId);
        if (bp == null) return b.standing = false;
        int total = 0;
        int matching = 0;
        for (Blueprint.Cell cell : bp.cells(b.rotation)) {
            if (cell.state().isAir()) continue;
            total++;
            if (level.getBlockState(b.origin.offset(cell.pos())).is(cell.state().getBlock())) matching++;
        }
        b.standing = total == 0 || matching >= total * STANDING_THRESHOLD;
        return b.standing;
    }

    /** Forgets the cached standing answer, so the next question reads the blocks again. */
    public void recheck(Building b) {
        b.standingCheckedAt = Long.MIN_VALUE;
    }

    // ---- Persistence ----

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Building b : buildings.values()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("Id", b.id);
            t.putUUID("Village", b.villageId);
            t.putString("Blueprint", b.blueprintId);
            t.putString("Purpose", b.purpose);
            t.putString("Rotation", b.rotation.name());
            t.put("Origin", NbtUtils.writeBlockPos(b.origin));
            t.putIntArray("Bounds", new int[]{b.bounds.minX(), b.bounds.minY(), b.bounds.minZ(),
                    b.bounds.maxX(), b.bounds.maxY(), b.bounds.maxZ()});
            ListTag slots = new ListTag();
            for (BarracksSlot s : b.slots) {
                CompoundTag st = new CompoundTag();
                st.putLong("Head", s.bedHead.asLong());
                st.putLong("Foot", s.bedFoot.asLong());
                if (s.chest != null) st.putLong("Chest", s.chest.asLong());
                st.putInt("Shift", s.shiftStart);
                if (s.warrior != null) st.putUUID("Warrior", s.warrior);
                st.putLong("DiedAt", s.diedAt);
                slots.add(st);
            }
            t.put("Slots", slots);

            if (b.paid != null) {
                ListTag cost = new ListTag();
                for (Blueprint.Cost c : b.paid) {
                    ResourceLocation key = ForgeRegistries.ITEMS.getKey(c.item());
                    if (key == null) continue;
                    CompoundTag ct = new CompoundTag();
                    ct.putString("Item", key.toString());
                    ct.putInt("Count", c.count());
                    cost.add(ct);
                }
                t.put("Paid", cost);
            }

            if (!b.originals.isEmpty()) {
                // A palette plus parallel arrays, the way construction sites keep theirs: a building is
                // hundreds of blocks, and a compound per block would bloat the save.
                Map<BlockState, Integer> paletteIndex = new HashMap<>();
                ListTag palette = new ListTag();
                long[] positions = new long[b.originals.size()];
                int[] states = new int[b.originals.size()];
                int i = 0;
                for (Map.Entry<BlockPos, BlockState> e : b.originals.entrySet()) {
                    positions[i] = e.getKey().asLong();
                    states[i] = paletteIndex.computeIfAbsent(e.getValue(), st -> {
                        palette.add(NbtUtils.writeBlockState(st));
                        return palette.size() - 1;
                    });
                    i++;
                }
                t.put("OriginalPalette", palette);
                t.putLongArray("OriginalPositions", positions);
                t.putIntArray("OriginalStates", states);
            }
            list.add(t);
        }
        tag.put("Buildings", list);
        return tag;
    }

    private static VillageBuildings load(ServerLevel level, CompoundTag tag) {
        var blocks = level.holderLookup(Registries.BLOCK);
        VillageBuildings data = new VillageBuildings();
        ListTag list = tag.getList("Buildings", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            Rotation rotation;
            try {
                rotation = Rotation.valueOf(t.getString("Rotation"));
            } catch (IllegalArgumentException ex) {
                rotation = Rotation.NONE;
            }
            int[] bb = t.getIntArray("Bounds");
            if (bb.length != 6) continue;
            Building b = new Building(t.getUUID("Id"), t.getUUID("Village"), t.getString("Blueprint"), t.getString("Purpose"),
                    rotation, NbtUtils.readBlockPos(t.getCompound("Origin")), new BoundingBox(bb[0], bb[1], bb[2], bb[3], bb[4], bb[5]));
            ListTag slots = t.getList("Slots", Tag.TAG_COMPOUND);
            for (int j = 0; j < slots.size(); j++) {
                CompoundTag st = slots.getCompound(j);
                BarracksSlot s = new BarracksSlot(BlockPos.of(st.getLong("Head")), BlockPos.of(st.getLong("Foot")),
                        st.contains("Chest") ? BlockPos.of(st.getLong("Chest")) : null, st.getInt("Shift"));
                if (st.hasUUID("Warrior")) s.warrior = st.getUUID("Warrior");
                s.diedAt = st.getLong("DiedAt");
                b.slots.add(s);
            }

            if (t.contains("Paid", Tag.TAG_LIST)) {
                List<Blueprint.Cost> paid = new ArrayList<>();
                ListTag cost = t.getList("Paid", Tag.TAG_COMPOUND);
                for (int j = 0; j < cost.size(); j++) {
                    CompoundTag ct = cost.getCompound(j);
                    ResourceLocation key = ResourceLocation.tryParse(ct.getString("Item"));
                    Item item = key != null ? ForgeRegistries.ITEMS.getValue(key) : null;
                    if (item != null) paid.add(new Blueprint.Cost(item, ct.getInt("Count")));
                }
                b.paid = paid;
            }

            ListTag palette = t.getList("OriginalPalette", Tag.TAG_COMPOUND);
            List<BlockState> paletteStates = new ArrayList<>(palette.size());
            for (int j = 0; j < palette.size(); j++) paletteStates.add(NbtUtils.readBlockState(blocks, palette.getCompound(j)));
            long[] positions = t.getLongArray("OriginalPositions");
            int[] states = t.getIntArray("OriginalStates");
            for (int j = 0; j < positions.length && j < states.length; j++) {
                if (states[j] >= 0 && states[j] < paletteStates.size()) {
                    b.originals.put(BlockPos.of(positions[j]), paletteStates.get(states[j]));
                }
            }
            data.buildings.put(b.id, b);
        }
        return data;
    }

    /** Package-private factory for {@link BarracksService}, which lays out a barracks' bunks. */
    static BarracksSlot newSlot(BlockPos head, BlockPos foot, @Nullable BlockPos chest, int shiftStart) {
        return new BarracksSlot(head, foot, chest, shiftStart);
    }
}
