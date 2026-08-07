package net.finnigan.tommemod.entity.custom.PurlingHelpers;

import net.finnigan.tommemod.entity.custom.PurlingEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Sends the purling over to a standing chorus plant to tear it down with the "break" animation. The
 * whole connected chain comes down at once (flood-filled and destroyed top-down) rather than a single
 * block, so the fruit it was after actually ends up on the floor for
 * {@link PurlingEatChorusFruitGoal} to pick off.
 */
public class PurlingBreakChorusGoal extends Goal {

    private static final int SEARCH_RADIUS_HORIZONTAL = 12;
    private static final int SEARCH_RADIUS_VERTICAL = 6;
    private static final double REACH_DISTANCE = 3.0;
    /** Length of the "break" animation (0.5s), in ticks. */
    private static final int BREAK_TICKS = 10;
    private static final int COOLDOWN_TICKS = 200;
    /** Only try to find a plant every ~6s - the block scan is not cheap. */
    private static final int SCAN_INTERVAL = 120;
    private static final int MAX_CHAIN_BLOCKS = 128;
    private static final int GIVE_UP_TICKS = 200;
    private static final double SPEED_MODIFIER = 1.0;

    private final PurlingEntity purling;
    private BlockPos target;
    private int breakTicks = -1;
    private int cooldown;
    private int ticksRunning;

    public PurlingBreakChorusGoal(PurlingEntity purling) {
        this.purling = purling;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        if (purling.getRandom().nextInt(SCAN_INTERVAL) != 0) return false;
        target = findChorusBlock();
        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        return target != null && isChorus(purling.level().getBlockState(target)) && ticksRunning < GIVE_UP_TICKS;
    }

    @Override
    public void start() {
        breakTicks = -1;
        ticksRunning = 0;
        purling.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, SPEED_MODIFIER);
    }

    @Override
    public void stop() {
        purling.getNavigation().stop();
        target = null;
        breakTicks = -1;
        cooldown = COOLDOWN_TICKS;
    }

    @Override
    public void tick() {
        if (target == null) return;
        ticksRunning++;
        purling.getLookControl().setLookAt(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);

        if (breakTicks >= 0) {
            if (--breakTicks < 0) {
                breakChain(purling.level(), target);
                target = null;
            }
            return;
        }

        if (purling.distanceToSqr(target.getX() + 0.5, target.getY(), target.getZ() + 0.5) <= REACH_DISTANCE * REACH_DISTANCE) {
            purling.getNavigation().stop();
            purling.triggerAnim("actionController", "break");
            breakTicks = BREAK_TICKS;
        } else if (purling.getNavigation().isDone()) {
            purling.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, SPEED_MODIFIER);
        }
    }

    private BlockPos findChorusBlock() {
        Level level = purling.level();
        BlockPos origin = purling.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-SEARCH_RADIUS_HORIZONTAL, -SEARCH_RADIUS_VERTICAL, -SEARCH_RADIUS_HORIZONTAL),
                origin.offset(SEARCH_RADIUS_HORIZONTAL, SEARCH_RADIUS_VERTICAL, SEARCH_RADIUS_HORIZONTAL))) {
            if (!isChorus(level.getBlockState(pos))) continue;
            double distance = purling.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = pos.immutable();
            }
        }
        return best;
    }

    /** Flood-fills the connected chorus blocks and destroys them highest-first so nothing is left floating. */
    private static void breakChain(Level level, BlockPos start) {
        List<BlockPos> chain = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start);

        while (!queue.isEmpty() && chain.size() < MAX_CHAIN_BLOCKS) {
            BlockPos pos = queue.poll();
            if (!isChorus(level.getBlockState(pos))) continue;
            chain.add(pos);
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction).immutable();
                if (seen.add(next)) queue.add(next);
            }
        }

        chain.sort(Comparator.comparingInt((BlockPos pos) -> pos.getY()).reversed());
        for (BlockPos pos : chain) {
            level.destroyBlock(pos, true);
        }
    }

    private static boolean isChorus(BlockState state) {
        return state.is(Blocks.CHORUS_PLANT) || state.is(Blocks.CHORUS_FLOWER);
    }
}
