package net.finnigan.tommemod.entity.custom.WarriorVillagerHelpers;

import net.finnigan.tommemod.entity.custom.WarriorVillagerEntity;
import net.finnigan.tommemod.village.WarriorKit;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import javax.annotation.Nullable;
import java.util.EnumSet;

/**
 * Every so often an idle Barracks Warrior checks the chest by its bed and, if the squires have left
 * something it wants (see WarriorKit), walks over and takes it.
 */
public class CollectKitGoal extends Goal {

    private static final int CHECK_INTERVAL = 100;
    private static final double REACH_SQR = 2.5 * 2.5;
    private static final int PATIENCE = 300;

    private final WarriorVillagerEntity warrior;
    private int cooldown;
    private int walking;

    public CollectKitGoal(WarriorVillagerEntity warrior) {
        this.warrior = warrior;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (--cooldown > 0) return false;
        cooldown = CHECK_INTERVAL;
        if (warrior.getTarget() != null || warrior.isSleeping()) return false;
        Container chest = chest();
        return chest != null && WarriorKit.anyWanted(warrior, chest);
    }

    @Override
    public boolean canContinueToUse() {
        return warrior.getTarget() == null && walking <= PATIENCE && chest() != null;
    }

    @Override
    public void start() {
        walking = 0;
        BlockPos pos = warrior.getBunkChest();
        warrior.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.8D);
    }

    @Override
    public void tick() {
        BlockPos pos = warrior.getBunkChest();
        warrior.getLookControl().setLookAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        if (warrior.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= REACH_SQR) {
            Container chest = chest();
            if (chest != null && WarriorKit.takeFrom(warrior, chest)) {
                warrior.level().playSound(null, pos, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.5F, 1.0F);
            }
            walking = PATIENCE + 1; // done
            return;
        }
        if (++walking % 40 == 0 && warrior.getNavigation().isDone()) {
            warrior.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.8D);
        }
    }

    @Override
    public void stop() {
        warrior.getNavigation().stop();
    }

    @Nullable
    private Container chest() {
        BlockPos pos = warrior.getBunkChest();
        if (pos == null) return null;
        return warrior.level().getBlockEntity(pos) instanceof ChestBlockEntity chest ? chest : null;
    }
}
