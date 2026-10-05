package net.finnigan.tommemod.village.blueprint;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Turns "this blueprint, this way round, with its corner here" into the exact list of block changes
 * builders will make, in the order they will make them - and says whether the spot is acceptable.
 *
 * <p>Runs on both sides from the same inputs. The client calls it every time the ghost moves, to
 * colour it and to explain a refusal before the player clicks; the server calls it again when the
 * click arrives, and its answer is the one that counts. Anything that needs village data the client
 * does not have (who is chief, what is affordable) is checked by the caller instead.
 */
public final class BlueprintPlanner {

    /** Build order. Builders clear the volume top-down, shore up the ground, raise the shell, and
     * only then add stairs, doors, torches and the rest - so nothing is hung on a wall that is not
     * there yet. */
    public static final int PHASE_CLEAR = 0;
    public static final int PHASE_FOUNDATION = 1;
    public static final int PHASE_STRUCTURE = 2;
    public static final int PHASE_DETAIL = 3;

    public record Placement(BlockPos pos, BlockState state, int phase) {
    }

    public record Plan(Blueprint blueprint, Rotation rotation, BlockPos origin, BoundingBox bounds,
                       List<Placement> placements, @Nullable Component problem) {
        public boolean valid() {
            return problem == null;
        }
    }

    private BlueprintPlanner() {
    }

    /** Where the design's corner goes so that it is centred on {@code ground}, with its floor
     * replacing that block (raised or sunk by {@code yOffset}). */
    public static BlockPos originFor(Blueprint bp, Rotation rotation, BlockPos ground, int yOffset) {
        return new BlockPos(ground.getX() - bp.width(rotation) / 2, ground.getY() + yOffset, ground.getZ() - bp.depth(rotation) / 2);
    }

    public static BoundingBox boundsFor(Blueprint bp, Rotation rotation, BlockPos origin) {
        return new BoundingBox(origin.getX(), origin.getY(), origin.getZ(),
                origin.getX() + bp.width(rotation) - 1, origin.getY() + bp.height() - 1, origin.getZ() + bp.depth(rotation) - 1);
    }

    /**
     * The ground under a targeted block: walks down through air, foliage, tree trunks and plants so
     * that pointing at a treetop or a tuft of grass still puts the building on the soil beneath.
     */
    public static BlockPos groundBelow(Level level, BlockPos hit) {
        BlockPos.MutableBlockPos p = hit.mutable();
        int floor = level.getMinBuildHeight();
        while (p.getY() > floor) {
            BlockState s = level.getBlockState(p);
            boolean passThrough = s.isAir() || s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS)
                    || (s.canBeReplaced() && s.getFluidState().isEmpty());
            if (!passThrough) break;
            p.move(0, -1, 0);
        }
        return p.immutable();
    }

    public static Plan plan(Level level, Blueprint bp, Rotation rotation, BlockPos origin, int maxGroundGap) {
        BoundingBox bounds = boundsFor(bp, rotation, origin);
        List<Placement> out = new ArrayList<>();
        Component problem = null;

        if (bounds.minY() < level.getMinBuildHeight() || bounds.maxY() >= level.getMaxBuildHeight()) {
            return new Plan(bp, rotation, origin, bounds, out, Component.literal("Outside the world's build height"));
        }

        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        for (Blueprint.Cell cell : bp.cells(rotation)) {
            BlockPos pos = origin.offset(cell.pos());
            if (!level.hasChunkAt(pos)) {
                return new Plan(bp, rotation, origin, bounds, List.of(), Component.literal("That area isn't loaded"));
            }
            BlockState current = level.getBlockState(pos);
            BlockState target = cell.state();

            if (!current.equals(target) && !(target.isAir() && current.isAir())) {
                if (problem == null) problem = protectedReason(level, pos, current);
                if (problem == null && target.isAir() && !current.getFluidState().isEmpty()) {
                    problem = Component.literal("Can't build inside water or lava");
                }
                int phase = target.isAir() ? PHASE_CLEAR : isStructural(target) ? PHASE_STRUCTURE : PHASE_DETAIL;
                out.add(new Placement(pos, target, phase));
            }

            // Fill under every floor block down to solid ground, so nothing is left hanging over a dip.
            if (cell.pos().getY() == 0 && !target.isAir()) {
                for (int dy = 1; ; dy++) {
                    probe.set(pos.getX(), pos.getY() - dy, pos.getZ());
                    if (probe.getY() < level.getMinBuildHeight()) break;
                    BlockState below = level.getBlockState(probe);
                    if (!needsFill(below)) break;
                    if (dy > maxGroundGap) {
                        if (problem == null) problem = Component.literal("Ground is too uneven (more than " + maxGroundGap + " blocks to fill)");
                        break;
                    }
                    out.add(new Placement(probe.immutable(), bp.foundation(), PHASE_FOUNDATION));
                }
            }
        }

        out.sort(BUILD_ORDER);
        return new Plan(bp, rotation, origin, bounds, out, problem);
    }

    /**
     * The plan with some extra block changes worked in - the ramps that join wall pieces sitting at
     * different heights (see {@link WallSnapping#connectors}). An extra replaces whatever the plan had
     * for that block, so a ramp can be cut into a wall as well as built onto one. Blocks holding a
     * block entity are left alone, and changes the world already has are dropped.
     */
    public static Plan withExtras(Level level, Plan plan, List<Placement> extras) {
        if (extras.isEmpty()) return plan;
        java.util.Map<BlockPos, Placement> byPos = new java.util.LinkedHashMap<>();
        for (Placement p : plan.placements()) byPos.put(p.pos(), p);
        for (Placement extra : extras) {
            BlockPos pos = extra.pos();
            if (!level.hasChunkAt(pos) || level.getBlockEntity(pos) != null) continue;
            byPos.remove(pos);
            if (!level.getBlockState(pos).equals(extra.state())) byPos.put(pos, new Placement(pos, extra.state(), phaseFor(extra.state())));
        }
        List<Placement> out = new ArrayList<>(byPos.values());
        out.sort(BUILD_ORDER);
        return new Plan(plan.blueprint(), plan.rotation(), plan.origin(), plan.bounds(), out, plan.problem());
    }

    private static int phaseFor(BlockState target) {
        return target.isAir() ? PHASE_CLEAR : isStructural(target) ? PHASE_STRUCTURE : PHASE_DETAIL;
    }

    /** What would make this block off limits to builders, or null if they may replace it. Chests,
     * beds, job sites and bedrock are never knocked down to make room - the player moves them. */
    @Nullable
    private static Component protectedReason(Level level, BlockPos pos, BlockState current) {
        if (current.isAir()) return null;
        boolean isProtected = level.getBlockEntity(pos) != null
                || current.getDestroySpeed(level, pos) < 0
                || PoiTypes.forState(current).isPresent();
        if (!isProtected) return null;
        return Component.literal("Would destroy ").append(current.getBlock().getName());
    }

    private static boolean needsFill(BlockState s) {
        return s.isAir() || s.canBeReplaced() || !s.getFluidState().isEmpty() || s.is(BlockTags.LEAVES);
    }

    private static boolean isStructural(BlockState s) {
        return Block.isShapeFullBlock(s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
    }

    /** Phase first; clearing works down from the top, everything else up from the bottom; within a
     * layer builders sweep in rows, alternating direction, the way a person would. */
    private static final Comparator<Placement> BUILD_ORDER = (a, b) -> {
        if (a.phase() != b.phase()) return Integer.compare(a.phase(), b.phase());
        int ay = a.pos().getY();
        int by = b.pos().getY();
        if (ay != by) return a.phase() == PHASE_CLEAR ? Integer.compare(by, ay) : Integer.compare(ay, by);
        int az = a.pos().getZ();
        int bz = b.pos().getZ();
        if (az != bz) return Integer.compare(az, bz);
        boolean reverse = (az & 1) == 1;
        return reverse ? Integer.compare(b.pos().getX(), a.pos().getX()) : Integer.compare(a.pos().getX(), b.pos().getX());
    };
}
