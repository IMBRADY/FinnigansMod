package net.finnigan.tommemod.village.construction;

import net.finnigan.tommemod.village.blueprint.Blueprint;
import net.finnigan.tommemod.village.blueprint.BlueprintPlanner.Placement;
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
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A building in progress: the ordered list of block changes still to make, a record of what each
 * changed block used to be (so a cancelled build can be put back exactly), and what was paid for it
 * (so it can be refunded).
 *
 * <p>Several builders work one site at once, so work is handed out rather than walked in order: a
 * builder {@link #claimNext claims} a placement, goes to it, and {@link #complete completes} it. A
 * builder that wanders off, dies or unloads {@link #release releases} its claim and the placement goes
 * back to the front of the queue. Claims are not saved; anything claimed when the world closes is
 * written back as released, so a restart never loses a block.
 */
public final class ConstructionSite {

    private final UUID id;
    private final UUID villageId;
    @Nullable
    private final UUID owner;
    private final String blueprintId;
    private final Rotation rotation;
    private final BlockPos origin;
    private final BoundingBox bounds;
    private final long createdTick;
    private final List<Placement> placements;
    private final List<Blueprint.Cost> paid;

    private int cursor;
    private int completed;
    private final Deque<Integer> returned = new ArrayDeque<>();
    private final Set<Integer> returnedSet = new HashSet<>();
    private final Set<Integer> outstanding = new HashSet<>();
    // Insertion-ordered so a cancel can undo in reverse; keyed by position so a block changed twice
    // (cleared, then built on) remembers what was there before builders ever touched it.
    private final LinkedHashMap<BlockPos, BlockState> originals = new LinkedHashMap<>();
    @Nullable
    private Map<BlockPos, Integer> indexByPos;

    public ConstructionSite(UUID id, UUID villageId, @Nullable UUID owner, String blueprintId, Rotation rotation,
                            BlockPos origin, BoundingBox bounds, long createdTick, List<Placement> placements,
                            List<Blueprint.Cost> paid) {
        this.id = id;
        this.villageId = villageId;
        this.owner = owner;
        this.blueprintId = blueprintId;
        this.rotation = rotation;
        this.origin = origin;
        this.bounds = bounds;
        this.createdTick = createdTick;
        this.placements = placements;
        this.paid = paid;
    }

    public UUID id() {
        return id;
    }

    public UUID villageId() {
        return villageId;
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    public String blueprintId() {
        return blueprintId;
    }

    @Nullable
    public Blueprint blueprint() {
        return Blueprints.get(blueprintId);
    }

    public String displayName() {
        Blueprint bp = blueprint();
        return bp != null ? bp.name() : blueprintId;
    }

    public Rotation rotation() {
        return rotation;
    }

    public BlockPos origin() {
        return origin;
    }

    public BoundingBox bounds() {
        return bounds;
    }

    public long createdTick() {
        return createdTick;
    }

    public List<Blueprint.Cost> paid() {
        return paid;
    }

    public int total() {
        return placements.size();
    }

    public int completedCount() {
        return completed;
    }

    public Placement placement(int index) {
        return placements.get(index);
    }

    public boolean isFinished() {
        return cursor >= placements.size() && returned.isEmpty() && outstanding.isEmpty();
    }

    /**
     * The next placement that still needs doing, now claimed by the caller - or null if there is
     * nothing that can be handed out right now. Placements the world already satisfies (a partner
     * door half placed alongside its other half, a block someone else put there) are completed on
     * the spot.
     *
     * <p>With several Builders working at once, the build order alone does not stop a crop going in
     * before the farmland under it, or a lantern before the beam it hangs from. So a placement is
     * held back while any block touching it that comes earlier in the order is still unbuilt. The
     * earliest unbuilt placement can never be held back, so the site always makes progress.
     */
    @Nullable
    public Integer claimNext(ServerLevel level) {
        List<Integer> heldBack = new ArrayList<>();
        Integer found = null;
        for (int attempts = 0; attempts < 64 && found == null; attempts++) {
            Integer next;
            if (!returned.isEmpty()) {
                next = returned.poll();
                returnedSet.remove(next);
            } else if (cursor < placements.size()) {
                next = cursor++;
            } else {
                break;
            }
            Placement p = placements.get(next);
            if (level.getBlockState(p.pos()).equals(p.state())) {
                completed++;
                continue;
            }
            if (waitsOnEarlierNeighbour(next)) {
                heldBack.add(next);
                continue;
            }
            found = next;
        }
        for (int i = heldBack.size() - 1; i >= 0; i--) pushReturned(heldBack.get(i));
        if (found != null) outstanding.add(found);
        return found;
    }

    private boolean waitsOnEarlierNeighbour(int index) {
        BlockPos pos = placements.get(index).pos();
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
            Integer other = indexAt(pos.relative(d));
            if (other != null && other < index && isPending(other)) return true;
        }
        return false;
    }

    private boolean isPending(int index) {
        return index >= cursor || returnedSet.contains(index) || outstanding.contains(index);
    }

    private void pushReturned(int index) {
        returned.addFirst(index);
        returnedSet.add(index);
    }

    public void release(int index) {
        if (outstanding.remove(index)) pushReturned(index);
    }

    public void complete(int index) {
        if (outstanding.remove(index)) completed++;
    }

    /** Records what stood at {@code pos} before builders changed it - only the first time. */
    public void recordOriginal(BlockPos pos, BlockState original) {
        originals.putIfAbsent(pos.immutable(), original);
    }

    /** Positions and their pre-construction states, most recently changed first. */
    public List<Map.Entry<BlockPos, BlockState>> originalsNewestFirst() {
        List<Map.Entry<BlockPos, BlockState>> list = new ArrayList<>(originals.entrySet());
        java.util.Collections.reverse(list);
        return list;
    }

    @Nullable
    public Integer indexAt(BlockPos pos) {
        if (indexByPos == null) {
            Map<BlockPos, Integer> map = new HashMap<>(placements.size() * 2);
            // Later placements win: a cleared cell that is then built on maps to the build.
            for (int i = 0; i < placements.size(); i++) map.put(placements.get(i).pos(), i);
            indexByPos = map;
        }
        return indexByPos.get(pos);
    }

    /** Whether a solid block is still due at {@code pos} - somewhere a builder should not stand. */
    public boolean solidPendingAt(ServerLevel level, BlockPos pos) {
        Integer idx = indexAt(pos);
        if (idx == null) return false;
        Placement p = placements.get(idx);
        return !p.state().isAir() && !level.getBlockState(pos).equals(p.state());
    }

    // ---- Persistence ----
    // Placements are stored as a palette plus two parallel arrays rather than a compound per block:
    // a large building is thousands of placements, and this keeps the save small and fast to read.

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putUUID("Village", villageId);
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putString("Blueprint", blueprintId);
        tag.putString("Rotation", rotation.name());
        tag.put("Origin", NbtUtils.writeBlockPos(origin));
        tag.putIntArray("Bounds", new int[]{bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ()});
        tag.putLong("Created", createdTick);

        Map<BlockState, Integer> paletteIndex = new HashMap<>();
        ListTag palette = new ListTag();
        long[] positions = new long[placements.size()];
        int[] states = new int[placements.size()];
        byte[] phases = new byte[placements.size()];
        for (int i = 0; i < placements.size(); i++) {
            Placement p = placements.get(i);
            positions[i] = p.pos().asLong();
            states[i] = paletteId(p.state(), paletteIndex, palette);
            phases[i] = (byte) p.phase();
        }
        long[] origPositions = new long[originals.size()];
        int[] origStates = new int[originals.size()];
        int o = 0;
        for (Map.Entry<BlockPos, BlockState> e : originals.entrySet()) {
            origPositions[o] = e.getKey().asLong();
            origStates[o] = paletteId(e.getValue(), paletteIndex, palette);
            o++;
        }
        tag.put("Palette", palette);
        tag.putLongArray("Positions", positions);
        tag.putIntArray("States", states);
        tag.putByteArray("Phases", phases);
        tag.putLongArray("OriginalPositions", origPositions);
        tag.putIntArray("OriginalStates", origStates);

        // Claimed-but-unplaced work is saved as returned, so nothing is skipped after a reload.
        List<Integer> pending = new ArrayList<>(outstanding);
        pending.addAll(returned);
        tag.putIntArray("Returned", pending.stream().mapToInt(Integer::intValue).toArray());
        tag.putInt("Cursor", cursor);
        tag.putInt("Completed", completed);

        ListTag cost = new ListTag();
        for (Blueprint.Cost c : paid) {
            CompoundTag ct = new CompoundTag();
            ResourceLocation key = ForgeRegistries.ITEMS.getKey(c.item());
            ct.putString("Item", key != null ? key.toString() : "minecraft:air");
            ct.putInt("Count", c.count());
            cost.add(ct);
        }
        tag.put("Paid", cost);
        return tag;
    }

    private static int paletteId(BlockState state, Map<BlockState, Integer> index, ListTag palette) {
        return index.computeIfAbsent(state, s -> {
            palette.add(NbtUtils.writeBlockState(s));
            return palette.size() - 1;
        });
    }

    public static ConstructionSite load(ServerLevel level, CompoundTag tag) {
        var blocks = level.holderLookup(Registries.BLOCK);
        ListTag paletteTag = tag.getList("Palette", Tag.TAG_COMPOUND);
        List<BlockState> palette = new ArrayList<>(paletteTag.size());
        for (int i = 0; i < paletteTag.size(); i++) palette.add(NbtUtils.readBlockState(blocks, paletteTag.getCompound(i)));

        long[] positions = tag.getLongArray("Positions");
        int[] states = tag.getIntArray("States");
        byte[] phases = tag.getByteArray("Phases");
        List<Placement> placements = new ArrayList<>(positions.length);
        for (int i = 0; i < positions.length; i++) {
            placements.add(new Placement(BlockPos.of(positions[i]), palette.get(states[i]), i < phases.length ? phases[i] : 2));
        }

        int[] b = tag.getIntArray("Bounds");
        BoundingBox bounds = b.length == 6 ? new BoundingBox(b[0], b[1], b[2], b[3], b[4], b[5]) : new BoundingBox(0, 0, 0, 0, 0, 0);

        List<Blueprint.Cost> paid = new ArrayList<>();
        ListTag cost = tag.getList("Paid", Tag.TAG_COMPOUND);
        for (int i = 0; i < cost.size(); i++) {
            CompoundTag ct = cost.getCompound(i);
            ResourceLocation key = ResourceLocation.tryParse(ct.getString("Item"));
            Item item = key != null ? ForgeRegistries.ITEMS.getValue(key) : null;
            if (item != null) paid.add(new Blueprint.Cost(item, ct.getInt("Count")));
        }

        Rotation rotation;
        try {
            rotation = Rotation.valueOf(tag.getString("Rotation"));
        } catch (IllegalArgumentException ex) {
            rotation = Rotation.NONE;
        }

        ConstructionSite site = new ConstructionSite(tag.getUUID("Id"), tag.getUUID("Village"),
                tag.hasUUID("Owner") ? tag.getUUID("Owner") : null, tag.getString("Blueprint"), rotation,
                NbtUtils.readBlockPos(tag.getCompound("Origin")), bounds, tag.getLong("Created"), placements, paid);

        site.cursor = tag.getInt("Cursor");
        site.completed = tag.getInt("Completed");
        for (int r : tag.getIntArray("Returned")) {
            if (r >= 0 && r < placements.size() && site.returnedSet.add(r)) site.returned.add(r);
        }
        long[] origPositions = tag.getLongArray("OriginalPositions");
        int[] origStates = tag.getIntArray("OriginalStates");
        for (int i = 0; i < origPositions.length && i < origStates.length; i++) {
            site.originals.put(BlockPos.of(origPositions[i]), palette.get(origStates[i]));
        }
        return site;
    }
}
