package net.finnigan.tommemod.village.blueprint;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * How wall pieces fit together: which ends of the existing wall are still open, where a new piece has
 * to go (and which way round) to join one, and how high a piece sits so it follows the ground.
 *
 * <p>Pieces join through their {@link Blueprint.Port ports}. A new piece joins an open port when one
 * of its own ports lands on the cell just past it, facing back, with the same side outside - which
 * pins down both its rotation and its position, so a wall always continues the way it was going,
 * parapet on the same side. Heights are left out of the match: each piece sits on its own stretch of
 * ground, which is what lets a wall climb a hill.
 *
 * <p>Plain logic with no client classes, so the server could check the same things.
 */
public final class WallSnapping {

    /** A wall piece in the world - built, under way, or about to be placed. */
    public record Piece(Blueprint blueprint, Rotation rotation, BlockPos origin) {
    }

    /** A port where it sits in the world. Only x and z mean anything. */
    public record WorldPort(BlockPos pos, Direction facing, Direction outside) {
        /** The cell a joining piece's port has to sit on. */
        public BlockPos joint() {
            return pos.relative(facing);
        }
    }

    /** Where a piece goes to join a port, and its own ports once there. */
    public record Snap(Rotation rotation, BlockPos origin, List<WorldPort> ports) {
    }

    private WallSnapping() {
    }

    public static List<WorldPort> ports(Blueprint bp, Rotation rotation, BlockPos origin) {
        List<WorldPort> out = new ArrayList<>();
        for (Blueprint.Port p : bp.ports(rotation)) {
            out.add(new WorldPort(new BlockPos(origin.getX() + p.pos().getX(), origin.getY(), origin.getZ() + p.pos().getZ()),
                    p.facing(), p.outside()));
        }
        return out;
    }

    /** Every port of these pieces that no other piece has joined. */
    public static List<WorldPort> openPorts(List<Piece> pieces) {
        List<WorldPort> all = new ArrayList<>();
        for (Piece p : pieces) all.addAll(ports(p.blueprint(), p.rotation(), p.origin()));
        Set<String> taken = new HashSet<>();
        for (WorldPort p : all) taken.add(key(p.pos(), p.facing()));
        List<WorldPort> open = new ArrayList<>();
        for (WorldPort p : all) {
            if (!taken.contains(key(p.joint(), p.facing().getOpposite()))) open.add(p);
        }
        return open;
    }

    private static String key(BlockPos pos, Direction facing) {
        return pos.getX() + "," + pos.getZ() + "," + facing.get2DDataValue();
    }

    /** Where {@code bp} goes to join {@code to}, or null if it has no port that fits. Y is left at the port's. */
    @Nullable
    public static Snap snap(Blueprint bp, WorldPort to) {
        for (Rotation rotation : Rotation.values()) {
            for (Blueprint.Port p : bp.ports(rotation)) {
                if (p.facing() != to.facing().getOpposite() || p.outside() != to.outside()) continue;
                BlockPos joint = to.joint();
                BlockPos origin = new BlockPos(joint.getX() - p.pos().getX(), to.pos().getY(), joint.getZ() - p.pos().getZ());
                return new Snap(rotation, origin, ports(bp, rotation, origin));
            }
        }
        return null;
    }

    /** The rotation of {@code bp} whose ports run along {@code along}, with the outside facing {@code outside}. */
    @Nullable
    public static Rotation rotationFor(Blueprint bp, Direction.Axis along, Direction outside) {
        for (Rotation rotation : Rotation.values()) {
            List<Blueprint.Port> ports = bp.ports(rotation);
            if (ports.isEmpty() || ports.get(0).facing().getAxis() != along) continue;
            if (ports.get(0).outside() == outside) return rotation;
        }
        return null;
    }

    /** Whether a piece is laid in a straight run when dragged: marked for it, with two ports facing opposite ways. */
    public static boolean runsStraight(Blueprint bp) {
        if (!bp.runs()) return false;
        List<Blueprint.Port> ports = bp.ports(Rotation.NONE);
        return ports.size() == 2 && ports.get(0).facing() == ports.get(1).facing().getOpposite();
    }

    /**
     * The height a wall piece's floor should sit at so it follows the ground: the middle value of the
     * ground heights under its footprint. Half the footprint ends up dug in a little, half stood on
     * foundation, and none of it floats. {@code fallback} if none of it is loaded.
     */
    public static int groundY(Level level, Blueprint bp, Rotation rotation, BlockPos origin, int fallback) {
        List<Integer> heights = groundHeights(level, bp, rotation, origin, fallback);
        if (heights.isEmpty()) return fallback;
        return heights.get(heights.size() / 2);
    }

    /**
     * The height for a piece joining a neighbour whose floor is at {@code neighbourY}: as close to the
     * ground as it can get without stepping more than one block from the neighbour, so a wall over
     * bumpy land stays level and builders fill under it instead. Two things let it step further:
     * a dip too deep to fill ({@code maxGap}) sends it down, and a slope that would bury it more than
     * {@link #MAX_CUT} blocks into the hill sends it up. Big steps are then bridged by
     * {@link #connectors}.
     */
    public static int fitY(Level level, Blueprint bp, Rotation rotation, BlockPos origin, int neighbourY, int maxGap) {
        List<Integer> heights = groundHeights(level, bp, rotation, origin, neighbourY);
        if (heights.isEmpty()) return neighbourY;
        int ground = heights.get(heights.size() / 2);
        int lowest = heights.get(0);
        int y = Math.max(neighbourY - 1, Math.min(neighbourY + 1, ground));
        if (y - lowest > maxGap) y = lowest + maxGap;
        if (ground - y > MAX_CUT) y = ground - MAX_CUT;
        return y;
    }

    /** Most a piece may sit below the ground under it before it steps up instead. */
    private static final int MAX_CUT = 3;
    /** Biggest height difference a ramp bridges - walkways have three blocks of headroom. */
    private static final int MAX_RAMP = 3;
    /** How far above the expected height the ground is looked for. */
    private static final int SEARCH_ABOVE = 12;

    /**
     * Stairs between this piece's walkway and the walkway of every neighbour it joins at a different
     * height, so the walk along the top of the wall never has a step you can't climb.
     *
     * <p>A ramp climbs over the last blocks of the lower piece's walkway, one step per block, with the
     * parapet built up beside it. Corners are curved, so a ramp more than one step long doesn't fit on
     * one: then it is cut down into the higher piece instead, if that is a straight wall. Pieces without
     * a walkway (gatehouses) get no ramp - their towers have ladders.
     */
    public static List<BlueprintPlanner.Placement> connectors(Blueprint bp, Rotation rotation, BlockPos origin, List<Piece> others) {
        List<BlueprintPlanner.Placement> out = new ArrayList<>();
        if (bp.walkway() < 0) return out;
        for (WorldPort mine : ports(bp, rotation, origin)) {
            for (Piece other : others) {
                if (other.blueprint().walkway() < 0 || other.origin().equals(origin)) continue;
                WorldPort theirs = null;
                for (WorldPort p : ports(other.blueprint(), other.rotation(), other.origin())) {
                    if (p.facing() == mine.facing().getOpposite() && samePlace(p.pos(), mine.joint())) theirs = p;
                }
                if (theirs == null) continue;
                int myWalk = origin.getY() + bp.walkway();
                int theirWalk = other.origin().getY() + other.blueprint().walkway();
                int d = Math.min(Math.abs(theirWalk - myWalk), MAX_RAMP);
                if (d == 0) continue;
                boolean iAmLower = myWalk < theirWalk;
                WorldPort lowPort = iAmLower ? mine : theirs;
                WorldPort highPort = iAmLower ? theirs : mine;
                Blueprint low = iAmLower ? bp : other.blueprint();
                Blueprint high = iAmLower ? other.blueprint() : bp;
                int lowWalk = Math.min(myWalk, theirWalk);
                if (low.runs() || d == 1) {
                    rampUp(out, lowPort, lowWalk, d);
                } else if (high.runs()) {
                    cutDown(out, highPort, lowWalk, d);
                } else {
                    rampUp(out, lowPort, lowWalk, 1);
                }
            }
        }
        return out;
    }

    private static boolean samePlace(BlockPos a, BlockPos b) {
        return a.getX() == b.getX() && a.getZ() == b.getZ();
    }

    /** Steps built up over the lower walkway's last {@code d} blocks, rising towards the joint. */
    private static void rampUp(List<BlueprintPlanner.Placement> out, WorldPort port, int walk, int d) {
        Direction towards = port.facing();
        for (int k = 0; k < d; k++) {
            BlockPos column = port.pos().relative(towards.getOpposite(), k);
            int stairY = walk + (d - 1 - k);
            for (BlockPos row : List.of(column, column.relative(port.outside().getOpposite()))) {
                for (int y = walk; y < stairY; y++) out.add(place(row, y, STONE));
                out.add(place(row, stairY, stair(towards)));
            }
            // Parapet filled solid alongside, so the ramp has a wall on its outside. Not past the
            // crenel layer, where the wall's own lanterns stand.
            BlockPos parapet = column.relative(port.outside());
            for (int y = walk; y <= Math.min(stairY + 1, walk + 1); y++) out.add(place(parapet, y, STONE));
        }
    }

    /** Steps cut down into the higher walkway's first {@code d} blocks, falling towards the joint. */
    private static void cutDown(List<BlueprintPlanner.Placement> out, WorldPort port, int lowWalk, int d) {
        Direction away = port.facing().getOpposite();
        int highWalk = lowWalk + d;
        for (int k = 0; k < d; k++) {
            BlockPos column = port.pos().relative(away, k);
            int stairY = lowWalk + k;
            for (BlockPos row : List.of(column, column.relative(port.outside().getOpposite()))) {
                out.add(place(row, stairY, stair(away)));
                for (int y = stairY + 1; y < highWalk; y++) out.add(place(row, y, Blocks.AIR.defaultBlockState()));
            }
        }
    }

    private static final BlockState STONE = Blocks.STONE_BRICKS.defaultBlockState();

    private static BlockState stair(Direction rising) {
        return Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, rising).setValue(StairBlock.HALF, Half.BOTTOM);
    }

    private static BlueprintPlanner.Placement place(BlockPos column, int y, BlockState state) {
        return new BlueprintPlanner.Placement(new BlockPos(column.getX(), y, column.getZ()), state, 0);
    }

    private static List<Integer> groundHeights(Level level, Blueprint bp, Rotation rotation, BlockPos origin, int nearY) {
        List<Integer> heights = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (Blueprint.Cell cell : bp.cells(rotation)) {
            if (cell.pos().getY() != 0 || cell.state().isAir()) continue;
            int x = origin.getX() + cell.pos().getX();
            int z = origin.getZ() + cell.pos().getZ();
            if (!seen.add(BlockPos.asLong(x, 0, z)) || !level.hasChunk(x >> 4, z >> 4)) continue;
            // From just above where the piece is expected, not the sky: a roof, an overhang or a
            // treetop high over the spot is not the ground the wall stands on.
            int top = Math.min(level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, nearY + SEARCH_ABOVE);
            heights.add(BlueprintPlanner.groundBelow(level, new BlockPos(x, top, z)).getY());
        }
        heights.sort(Integer::compare);
        return heights;
    }
}
