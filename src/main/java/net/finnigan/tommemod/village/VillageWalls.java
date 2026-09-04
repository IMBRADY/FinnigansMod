package net.finnigan.tommemod.village;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * The ring a village's walls were raised along, and the authority on what is inside them.
 *
 * <p>Stored as the shape the perimeter was <em>derived</em> from rather than as the list of blocks it
 * came out as: the discs of the POIs the village had when the walls went up, and the radius each of
 * those projected. Everything a caller wants to ask is cheap against that and awkward against a block
 * list - "is this position inside?" is a distance check rather than a point-in-irregular-polygon test,
 * and the ring survives a player mining a hole in the wall, which a block list would not.
 *
 * <p>That the shape is frozen at build time is the point, not a limitation. A village's POI-derived
 * footprint drifts every time someone places a bed; its walls do not, and after they are up they -
 * not the POIs - are what {@link VillageManager} calls the village.
 */
public final class VillageWalls {

    private final List<BlockPos> discCentres;
    private final int discRadius;

    /**
     * The ring's bounding box, computed once at construction.
     *
     * <p>Not an optimisation looking for a problem: {@link VillageManager#resolveVillage} asks every
     * walled village whether it contains a position, and that runs on combat and trade events. Without
     * this, every one of those is a distance check against every POI of every walled village on the
     * server - and the answer is almost always no, which is exactly the case a box rejects in four
     * comparisons.
     */
    private final int minX;
    private final int maxX;
    private final int minZ;
    private final int maxZ;

    public VillageWalls(List<BlockPos> discCentres, int discRadius) {
        this.discCentres = List.copyOf(discCentres);
        this.discRadius = discRadius;

        int lowX = Integer.MAX_VALUE, highX = Integer.MIN_VALUE;
        int lowZ = Integer.MAX_VALUE, highZ = Integer.MIN_VALUE;
        for (BlockPos centre : this.discCentres) {
            lowX = Math.min(lowX, centre.getX() - discRadius);
            highX = Math.max(highX, centre.getX() + discRadius);
            lowZ = Math.min(lowZ, centre.getZ() - discRadius);
            highZ = Math.max(highZ, centre.getZ() + discRadius);
        }
        this.minX = lowX;
        this.maxX = highX;
        this.minZ = lowZ;
        this.maxZ = highZ;
    }

    public List<BlockPos> discCentres() {
        return discCentres;
    }

    public int discRadius() {
        return discRadius;
    }

    /** Whether this column is on or inside the wall. Flat: a wall encloses ground, not a volume. */
    public boolean contains(int x, int z) {
        if (x < minX || x > maxX || z < minZ || z > maxZ) return false;

        long radiusSqr = (long) discRadius * discRadius;
        for (BlockPos centre : discCentres) {
            long dx = x - centre.getX();
            long dz = z - centre.getZ();
            if (dx * dx + dz * dz <= radiusSqr) return true;
        }
        return false;
    }

    public boolean contains(BlockPos pos) {
        return contains(pos.getX(), pos.getZ());
    }

    /**
     * The columns the wall itself stands on: inside cells with at least one of their four neighbours
     * outside. Walks the ring's bounding box once, which is the only pass over it anyone needs - the
     * result is either built into blocks straight away or thrown away.
     */
    public List<BlockPos> perimeterColumns() {
        if (discCentres.isEmpty()) return List.of();

        int width = maxX - minX + 1;
        int depth = maxZ - minZ + 1;
        boolean[] inside = new boolean[width * depth];
        for (int z = 0; z < depth; z++) {
            for (int x = 0; x < width; x++) {
                inside[x + z * width] = contains(minX + x, minZ + z);
            }
        }

        List<BlockPos> perimeter = new ArrayList<>();
        for (int z = 0; z < depth; z++) {
            for (int x = 0; x < width; x++) {
                if (!inside[x + z * width]) continue;
                boolean edge = x == 0 || x == width - 1 || z == 0 || z == depth - 1
                        || !inside[(x - 1) + z * width]
                        || !inside[(x + 1) + z * width]
                        || !inside[x + (z - 1) * width]
                        || !inside[x + (z + 1) * width];
                if (edge) perimeter.add(new BlockPos(minX + x, 0, minZ + z));
            }
        }
        return perimeter;
    }

    /** Middle of the ring's bounding box - the point to measure a village's reach from. */
    public BlockPos centre() {
        if (discCentres.isEmpty()) return BlockPos.ZERO;

        long sumX = 0;
        long sumZ = 0;
        int sumY = 0;
        for (BlockPos centre : discCentres) {
            sumX += centre.getX();
            sumZ += centre.getZ();
            sumY += centre.getY();
        }
        int n = discCentres.size();
        return new BlockPos((int) (sumX / n), sumY / n, (int) (sumZ / n));
    }

    /** How far the wall gets from {@link #centre()} - i.e. a circle guaranteed to enclose it. */
    public double radiusFromCentre() {
        BlockPos centre = centre();
        double farthest = 0;
        for (BlockPos disc : discCentres) {
            double d = Math.sqrt(sqr(disc.getX() - centre.getX()) + sqr(disc.getZ() - centre.getZ()));
            if (d > farthest) farthest = d;
        }
        return farthest + discRadius;
    }

    private static double sqr(double v) {
        return v * v;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Radius", discRadius);
        ListTag list = new ListTag();
        for (BlockPos centre : discCentres) {
            CompoundTag e = new CompoundTag();
            e.putInt("X", centre.getX());
            e.putInt("Y", centre.getY());
            e.putInt("Z", centre.getZ());
            list.add(e);
        }
        tag.put("Discs", list);
        return tag;
    }

    public static VillageWalls load(CompoundTag tag) {
        List<BlockPos> centres = new ArrayList<>();
        ListTag list = tag.getList("Discs", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            centres.add(new BlockPos(e.getInt("X"), e.getInt("Y"), e.getInt("Z")));
        }
        return new VillageWalls(centres, tag.getInt("Radius"));
    }
}
