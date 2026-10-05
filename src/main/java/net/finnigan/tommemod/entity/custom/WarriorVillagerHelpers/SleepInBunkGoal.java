package net.finnigan.tommemod.entity.custom.WarriorVillagerHelpers;

import net.finnigan.tommemod.entity.custom.WarriorVillagerEntity;
import net.finnigan.tommemod.village.buildings.BarracksService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;

/**
 * A Barracks Warrior goes to its bed and sleeps through its shift (see BarracksService for how
 * shifts are staggered). Anything worth fighting ends the shift early: a target - whether it picked
 * one itself or answered an ally - stops this goal and the Warrior gets up. Being hit wakes any
 * sleeping entity anyway. Nobody sleeps through a raid: while one is on, the whole garrison stays up.
 */
public class SleepInBunkGoal extends Goal {

    private static final double REACH_SQR = 2.25 * 2.25;
    /** Ticks it may spend walking to bed before it just turns up there. */
    private static final int PATIENCE = 400;

    private final WarriorVillagerEntity warrior;
    private int walking;

    public SleepInBunkGoal(WarriorVillagerEntity warrior) {
        this.warrior = warrior;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        return wantsToSleep() && !bedTakenByAnother();
    }

    @Override
    public boolean canContinueToUse() {
        return wantsToSleep();
    }

    private boolean wantsToSleep() {
        BlockPos bed = warrior.getBunkBed();
        if (bed == null || warrior.getTarget() != null) return false;
        if (warrior.level() instanceof ServerLevel level && level.isRaided(warrior.blockPosition())) return false;
        if (!(warrior.level().getBlockState(bed).getBlock() instanceof BedBlock)) return false;
        return BarracksService.isSleepShift(warrior.level().getDayTime(), warrior.getShiftStart());
    }

    private boolean bedTakenByAnother() {
        BlockState state = warrior.level().getBlockState(warrior.getBunkBed());
        return state.hasProperty(BedBlock.OCCUPIED) && state.getValue(BedBlock.OCCUPIED) && !warrior.isSleeping();
    }

    @Override
    public void start() {
        walking = 0;
        BlockPos bed = warrior.getBunkBed();
        warrior.getNavigation().moveTo(bed.getX() + 0.5, bed.getY(), bed.getZ() + 0.5, 0.8D);
    }

    @Override
    public void tick() {
        if (warrior.isSleeping()) return;
        BlockPos bed = warrior.getBunkBed();
        if (warrior.distanceToSqr(bed.getX() + 0.5, bed.getY() + 0.5, bed.getZ() + 0.5) <= REACH_SQR || ++walking > PATIENCE) {
            warrior.getNavigation().stop();
            warrior.startSleeping(bed);
            return;
        }
        if (warrior.getNavigation().isDone()) {
            warrior.getNavigation().moveTo(bed.getX() + 0.5, bed.getY(), bed.getZ() + 0.5, 0.8D);
        }
    }

    @Override
    public void stop() {
        if (warrior.isSleeping()) warrior.stopSleeping();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}
