package net.finnigan.tommemod.entity.custom.PurlingHelpers;

import net.finnigan.tommemod.entity.custom.PurlingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/**
 * Highest-priority purling behaviour: walk up to a chorus fruit lying on the ground, play "eat", and
 * consume the item outright (it is destroyed, not picked up).
 */
public class PurlingEatChorusFruitGoal extends Goal {

    private static final double SEARCH_RADIUS = 16.0;
    private static final double REACH_DISTANCE = 1.75;
    /** Length of the "eat" animation (1.375s), in ticks. */
    private static final int EAT_TICKS = 28;
    private static final int COOLDOWN_TICKS = 40;
    private static final double SPEED_MODIFIER = 1.1;

    private final PurlingEntity purling;
    private ItemEntity quarry;
    private int eatTicks = -1;
    private int cooldown;

    public PurlingEatChorusFruitGoal(PurlingEntity purling) {
        this.purling = purling;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        quarry = findChorusFruit();
        return quarry != null;
    }

    @Override
    public boolean canContinueToUse() {
        return quarry != null && quarry.isAlive();
    }

    @Override
    public void start() {
        eatTicks = -1;
        purling.getNavigation().moveTo(quarry, SPEED_MODIFIER);
    }

    @Override
    public void stop() {
        purling.getNavigation().stop();
        quarry = null;
        eatTicks = -1;
        cooldown = COOLDOWN_TICKS;
    }

    @Override
    public void tick() {
        if (quarry == null) return;
        purling.getLookControl().setLookAt(quarry, 30.0F, 30.0F);

        if (eatTicks >= 0) {
            if (--eatTicks < 0) {
                quarry.discard();
                quarry = null;
            }
            return;
        }

        if (purling.distanceTo(quarry) <= REACH_DISTANCE) {
            purling.getNavigation().stop();
            purling.triggerAnim("actionController", "eat");
            eatTicks = EAT_TICKS;
        } else if (purling.getNavigation().isDone()) {
            purling.getNavigation().moveTo(quarry, SPEED_MODIFIER);
        }
    }

    private ItemEntity findChorusFruit() {
        List<ItemEntity> candidates = purling.level().getEntitiesOfClass(ItemEntity.class,
                purling.getBoundingBox().inflate(SEARCH_RADIUS),
                item -> item.isAlive() && item.getItem().is(Items.CHORUS_FRUIT));
        return candidates.stream().min(Comparator.comparingDouble(purling::distanceToSqr)).orElse(null);
    }
}
