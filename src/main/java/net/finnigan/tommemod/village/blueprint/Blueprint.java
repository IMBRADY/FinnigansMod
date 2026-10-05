package net.finnigan.tommemod.village.blueprint;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.finnigan.tommemod.village.buildings.BuildingPurpose;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One building design, as read from {@code data/tommemod/blueprints/<id>.json} (see {@link Blueprints}).
 *
 * <p>A design is a stack of layers, each a grid of characters looked up in a palette. Layer 0 sits at
 * ground level - it replaces the top block of the terrain, so a floor is flush with the grass around
 * it. Rows run north to south and columns west to east, and the front door faces south; every other
 * facing is produced by {@link #cells(Rotation)}, never authored.
 *
 * <p>Two characters are reserved. {@code .} (and a space) above layer 0 mean "this must be air", so
 * builders clear terrain and trees out of the building's volume; on layer 0 they mean "leave the
 * ground alone", which is how a design keeps grass inside a fence. {@code ~} leaves the cell untouched
 * on any layer.
 */
public final class Blueprint {

    /** A position relative to the design's own north-west-bottom corner, and what belongs there. */
    public record Cell(BlockPos pos, BlockState state) {
    }

    public record Cost(Item item, int count) {
    }

    /**
     * Where another wall piece may join on: the edge cell at the middle of the joint, the way the
     * joint faces, and which side of the wall is outside there. Two pieces join when one's port sits
     * just past the other's, facing back at it, with the same side outside.
     */
    public record Port(BlockPos pos, Direction facing, Direction outside) {
    }

    private final String id;
    private final String name;
    private final String description;
    private final String category;
    private final String purpose;
    private final String purposeText;
    private final Item icon;
    private final int requiredBuilders;
    private final BlockState foundation;
    private final List<Cost> cost;
    private final int width;
    private final int height;
    private final int depth;
    private final List<Cell> cells;
    private final List<Port> ports;
    private final boolean runs;
    private final int walkway;
    @SuppressWarnings("unchecked")
    private final List<Cell>[] rotated = new List[4];

    Blueprint(String id, String name, String description, String category, String purpose, String purposeText,
              Item icon, int requiredBuilders, BlockState foundation, List<Cost> cost, int width, int height, int depth,
              List<Cell> cells, List<Port> ports, boolean runs, int walkway) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
        this.purpose = purpose;
        this.purposeText = purposeText;
        this.icon = icon;
        this.requiredBuilders = requiredBuilders;
        this.foundation = foundation;
        this.cost = List.copyOf(cost);
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.cells = List.copyOf(cells);
        this.ports = List.copyOf(ports);
        this.runs = runs;
        this.walkway = walkway;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public String category() {
        return category;
    }

    /**
     * What this building does for the village once it stands - one of the ids in
     * {@link net.finnigan.tommemod.village.buildings.BuildingPurpose} - or empty for a building that
     * is just a building.
     */
    public String purpose() {
        return purpose;
    }

    public boolean hasPurpose() {
        return !purpose.isEmpty();
    }

    /** A piece of the village wall: snaps onto other pieces, follows the ground, and counts towards the loop. */
    public boolean isWallPiece() {
        return BuildingPurpose.WALL.equals(purpose);
    }

    /** Whether blueprint mode lays this piece in a run when the player drags - straight walls, not gatehouses. */
    public boolean runs() {
        return runs;
    }

    /** The layer a wall piece's walkway is stood in, or -1 if it has none to join up (a gatehouse). */
    public int walkway() {
        return walkway;
    }

    /** This piece's ports turned to face {@code rotation}, in the same footprint coordinates as its cells. */
    public List<Port> ports(Rotation rotation) {
        List<Port> out = new ArrayList<>(ports.size());
        for (Port p : ports) out.add(new Port(rotate(p.pos(), rotation), rotation.rotate(p.facing()), rotation.rotate(p.outside())));
        return out;
    }

    /** The player-facing line explaining {@link #purpose()}. */
    public String purposeText() {
        return purposeText;
    }

    public Item icon() {
        return icon;
    }

    public int requiredBuilders() {
        return requiredBuilders;
    }

    public BlockState foundation() {
        return foundation;
    }

    public List<Cost> cost() {
        return cost;
    }

    public int height() {
        return height;
    }

    /** Extent along X once rotated - a quarter turn swaps width and depth. */
    public int width(Rotation rotation) {
        return swapsAxes(rotation) ? depth : width;
    }

    /** Extent along Z once rotated. */
    public int depth(Rotation rotation) {
        return swapsAxes(rotation) ? width : depth;
    }

    /** How many cells actually place a block (not counting clearing), for the catalog. */
    public int solidBlockCount() {
        int n = 0;
        for (Cell c : cells) if (!c.state().isAir()) n++;
        return n;
    }

    /**
     * Every cell of the design turned to face {@code rotation} (south being unrotated). Positions are
     * rotated within the footprint so they stay non-negative, and each block state is rotated with
     * them, so stairs, doors, logs and beds come out facing the right way. Cached per rotation.
     */
    public List<Cell> cells(Rotation rotation) {
        int idx = rotation.ordinal();
        List<Cell> cached = rotated[idx];
        if (cached != null) return cached;

        List<Cell> out = new ArrayList<>(cells.size());
        for (Cell c : cells) {
            BlockPos p = c.pos();
            out.add(new Cell(rotate(p, rotation), c.state().rotate(rotation)));
        }
        cached = Collections.unmodifiableList(out);
        rotated[idx] = cached;
        return cached;
    }

    private BlockPos rotate(BlockPos p, Rotation rotation) {
        int x = p.getX();
        int z = p.getZ();
        return switch (rotation) {
            // Rotation.CLOCKWISE_90 takes north to east, i.e. (x, z) -> (-z, x); the extra terms
            // translate the result back into the footprint's own non-negative coordinates.
            case CLOCKWISE_90 -> new BlockPos(depth - 1 - z, p.getY(), x);
            case CLOCKWISE_180 -> new BlockPos(width - 1 - x, p.getY(), depth - 1 - z);
            case COUNTERCLOCKWISE_90 -> new BlockPos(z, p.getY(), width - 1 - x);
            default -> p;
        };
    }

    private static boolean swapsAxes(Rotation rotation) {
        return rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90;
    }

    static BlockState air() {
        return Blocks.AIR.defaultBlockState();
    }
}
