package net.finnigan.tommemod.village;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * The ground a village's walls enclose, and the authority on what is inside them.
 *
 * <p>Stored as a flat map of columns over the ring's bounding box: which columns are inside (the
 * walls themselves included) and which of those are wall. Walls are built from blueprint pieces -
 * straight runs, corners, gatehouses - so the shape is whatever loop the Chief closed, and "is this
 * position inside?" stays a single lookup however irregular that loop is. See
 * {@link net.finnigan.tommemod.village.buildings.WallPerimeter} for how the loop is found.
 *
 * <p>Saves from before blueprint walls hold the old shape instead - the POI discs the Chief Desk's
 * log wall was traced along - and load into the same map, so those villages keep their boundary.
 */
public final class VillageWalls {

    private final int minX;
    private final int minZ;
    private final int width;
    private final int depth;
    private final int y;
    /** Inside the ring, walls included. Indexed {@code x + z * width} from the corner. */
    private final BitSet inside;
    /** The columns the wall pieces themselves stand on. Empty for a ring loaded from an old save. */
    private final BitSet wall;
    /** Whether this ring came from blueprint walls (and so should go when they do). */
    private final boolean fromBlueprints;

    public VillageWalls(int minX, int minZ, int width, int depth, int y, BitSet inside, BitSet wall, boolean fromBlueprints) {
        this.minX = minX;
        this.minZ = minZ;
        this.width = Math.max(width, 0);
        this.depth = Math.max(depth, 0);
        this.y = y;
        this.inside = inside;
        this.wall = wall;
        this.fromBlueprints = fromBlueprints;
    }

    /** The old disc-union shape, rasterised. */
    public static VillageWalls fromDiscs(List<BlockPos> centres, int radius) {
        if (centres.isEmpty()) return new VillageWalls(0, 0, 0, 0, 0, new BitSet(), new BitSet(), false);
        int lowX = Integer.MAX_VALUE, highX = Integer.MIN_VALUE, lowZ = Integer.MAX_VALUE, highZ = Integer.MIN_VALUE;
        long sumY = 0;
        for (BlockPos c : centres) {
            lowX = Math.min(lowX, c.getX() - radius);
            highX = Math.max(highX, c.getX() + radius);
            lowZ = Math.min(lowZ, c.getZ() - radius);
            highZ = Math.max(highZ, c.getZ() + radius);
            sumY += c.getY();
        }
        int width = highX - lowX + 1;
        int depth = highZ - lowZ + 1;
        BitSet inside = new BitSet(width * depth);
        long radiusSqr = (long) radius * radius;
        for (BlockPos c : centres) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    if ((long) dx * dx + (long) dz * dz > radiusSqr) continue;
                    inside.set((c.getX() + dx - lowX) + (c.getZ() + dz - lowZ) * width);
                }
            }
        }
        return new VillageWalls(lowX, lowZ, width, depth, (int) (sumY / centres.size()), inside, new BitSet(), false);
    }

    public boolean isFromBlueprints() {
        return fromBlueprints;
    }

    public boolean isEmpty() {
        return inside.isEmpty();
    }

    /** Whether this column is on or inside the wall. Flat: a wall encloses ground, not a volume. */
    public boolean contains(int x, int z) {
        int lx = x - minX;
        int lz = z - minZ;
        if (lx < 0 || lz < 0 || lx >= width || lz >= depth) return false;
        return inside.get(lx + lz * width);
    }

    public boolean contains(BlockPos pos) {
        return contains(pos.getX(), pos.getZ());
    }

    /** The outermost inside columns: inside, with a neighbour that isn't. */
    public List<BlockPos> perimeterColumns() {
        List<BlockPos> out = new ArrayList<>();
        for (int i = inside.nextSetBit(0); i >= 0; i = inside.nextSetBit(i + 1)) {
            int lx = i % width;
            int lz = i / width;
            boolean edge = lx == 0 || lz == 0 || lx == width - 1 || lz == depth - 1
                    || !inside.get(i - 1) || !inside.get(i + 1) || !inside.get(i - width) || !inside.get(i + width);
            if (edge) out.add(new BlockPos(minX + lx, y, minZ + lz));
        }
        return out;
    }

    /** Every column a wall piece stands on. */
    public List<BlockPos> wallColumns() {
        List<BlockPos> out = new ArrayList<>(wall.cardinality());
        for (int i = wall.nextSetBit(0); i >= 0; i = wall.nextSetBit(i + 1)) {
            out.add(new BlockPos(minX + i % width, y, minZ + i / width));
        }
        return out;
    }

    /** Middle of the ring's bounding box - the point to measure a village's reach from. */
    public BlockPos centre() {
        return new BlockPos(minX + width / 2, y, minZ + depth / 2);
    }

    /** A circle round {@link #centre()} guaranteed to enclose the ring. */
    public double radiusFromCentre() {
        return Math.sqrt((double) width * width + (double) depth * depth) / 2.0;
    }

    /** Both rings at once - two walled villages that merged are enclosed by both walls. */
    public static VillageWalls union(VillageWalls a, VillageWalls b) {
        if (a.isEmpty()) return b;
        if (b.isEmpty()) return a;
        int lowX = Math.min(a.minX, b.minX);
        int lowZ = Math.min(a.minZ, b.minZ);
        int width = Math.max(a.minX + a.width, b.minX + b.width) - lowX;
        int depth = Math.max(a.minZ + a.depth, b.minZ + b.depth) - lowZ;
        BitSet inside = new BitSet(width * depth);
        BitSet wall = new BitSet(width * depth);
        for (VillageWalls w : List.of(a, b)) {
            for (int i = w.inside.nextSetBit(0); i >= 0; i = w.inside.nextSetBit(i + 1)) {
                inside.set((w.minX + i % w.width - lowX) + (w.minZ + i / w.width - lowZ) * width);
            }
            for (int i = w.wall.nextSetBit(0); i >= 0; i = w.wall.nextSetBit(i + 1)) {
                wall.set((w.minX + i % w.width - lowX) + (w.minZ + i / w.width - lowZ) * width);
            }
        }
        return new VillageWalls(lowX, lowZ, width, depth, (a.y + b.y) / 2, inside, wall, a.fromBlueprints || b.fromBlueprints);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("MinX", minX);
        tag.putInt("MinZ", minZ);
        tag.putInt("Width", width);
        tag.putInt("Depth", depth);
        tag.putInt("Y", y);
        tag.putLongArray("Inside", inside.toLongArray());
        tag.putLongArray("Wall", wall.toLongArray());
        tag.putBoolean("FromBlueprints", fromBlueprints);
        return tag;
    }

    public static VillageWalls load(CompoundTag tag) {
        if (tag.contains("Discs", Tag.TAG_LIST)) {
            List<BlockPos> centres = new ArrayList<>();
            ListTag list = tag.getList("Discs", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag e = list.getCompound(i);
                centres.add(new BlockPos(e.getInt("X"), e.getInt("Y"), e.getInt("Z")));
            }
            return fromDiscs(centres, tag.getInt("Radius"));
        }
        return new VillageWalls(tag.getInt("MinX"), tag.getInt("MinZ"), tag.getInt("Width"), tag.getInt("Depth"), tag.getInt("Y"),
                BitSet.valueOf(tag.getLongArray("Inside")), BitSet.valueOf(tag.getLongArray("Wall")), tag.getBoolean("FromBlueprints"));
    }
}
