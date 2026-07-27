package net.finnigan.tommemod.entity.custom.WarriorVillagerHelpers;

import net.finnigan.tommemod.item.custom.MusketItem;
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
 */
public class MeleeUnlessRangedAttackGoal extends MeleeAttackGoal {

    private final PathfinderMob mob;

    public MeleeUnlessRangedAttackGoal(PathfinderMob mob, double speedModifier, boolean followingTargetEvenIfNotSeen) {
        super(mob, speedModifier, followingTargetEvenIfNotSeen);
        this.mob = mob;
    }

    @Override
    public boolean canUse() {
        return !isHoldingRangedWeapon() && super.canUse();
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
