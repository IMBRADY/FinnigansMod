package net.finnigan.tommemod.entity.custom.WarriorVillagerHelpers;

import net.finnigan.tommemod.item.custom.MusketItem;
import net.finnigan.tommemod.mixin.MeleeAttackGoalAccessor;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;

/**
 * Vanilla MeleeAttackGoal never re-checks what weapon is held once it's running, and GoalSelector only
 * lets a higher-priority goal claim shared flags when it *starts* - it won't interrupt a goal that's
 * already running and still valid. So a Warrior that started charging in with the Halberd kept charging
 * even after the Chief swapped it to a bow/crossbow/musket mid-fight; the ranged goal (priority 1) never
 * got a chance to take over. Ceding here the instant a ranged weapon is held frees the flags immediately,
 * so the matching ranged goal picks up on the same tick instead of waiting for the target to die/flee.
 *
 * It also drops vanilla's refusal to even look at its target more than once a second - see canUse.
 */
public class MeleeUnlessRangedAttackGoal extends MeleeAttackGoal {

    /** Vanilla's COOLDOWN_BETWEEN_CAN_USE_CHECKS, which is private. */
    private static final long VANILLA_CAN_USE_COOLDOWN_TICKS = 20L;

    private final PathfinderMob mob;

    public MeleeUnlessRangedAttackGoal(PathfinderMob mob, double speedModifier, boolean followingTargetEvenIfNotSeen) {
        super(mob, speedModifier, followingTargetEvenIfNotSeen);
        this.mob = mob;
    }

    /**
     * Vanilla answers "no" outright to any canUse asked within a second of the last one, to spare the
     * pathfinder on crowds of ordinary mobs. On a village defender that second is the whole problem:
     * finish off one raider and the next charge doesn't begin until the cooldown lapses, and every time
     * the goal drops for a moment - a finished path, a target stepping out of reach - it is paid again.
     * Winding the timestamp back spends one path query per goal tick on a handful of Warriors, and buys
     * back a pause long enough to be plainly visible.
     */
    @Override
    public boolean canUse() {
        if (isHoldingRangedWeapon()) return false;

        ((MeleeAttackGoalAccessor) this).tommemod$setLastCanUseCheck(
                this.mob.level().getGameTime() - VANILLA_CAN_USE_COOLDOWN_TICKS);
        return super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return !isHoldingRangedWeapon() && super.canContinueToUse();
    }

    private boolean isHoldingRangedWeapon() {
        return this.mob.isHolding(is -> is.getItem() instanceof CrossbowItem
                || is.getItem() instanceof BowItem
                || is.getItem() instanceof MusketItem);
    }
}
