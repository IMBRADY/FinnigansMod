package net.finnigan.tommemod.mixin;

import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the timestamp behind MeleeAttackGoal's once-a-second limit on re-evaluating canUse().
 *
 * The limit exists to keep crowds of ordinary mobs from re-pathing every tick, and for them it is a
 * fair trade. For a village's Warriors it is not: see
 * entity.custom.WarriorVillagerHelpers.MeleeUnlessRangedAttackGoal, which winds the timestamp back so
 * a defender can pick its next fight the moment there is one.
 */
@Mixin(MeleeAttackGoal.class)
public interface MeleeAttackGoalAccessor {

    @Accessor("lastCanUseCheck")
    void tommemod$setLastCanUseCheck(long gameTime);
}
