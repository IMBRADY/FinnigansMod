package net.finnigan.tommemod.mixin;

import net.finnigan.tommemod.skill.event.SkillDefenseBonuses;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Defense's Unmoved: a blocking player is shoved less, but still shoved.
 *
 * Scales the strength on the way into {@code knockback} rather than cancelling the call, which is the
 * difference between "reduced" and "immune" - knockback immunity turns a shield into a wall and takes
 * the positioning out of a fight, which is why the node is capped well short of it.
 *
 * Distinct from the dodge suppression in {@link LivingEntityTotemMixin}, which cancels knockback
 * outright for a Lucky Dice dodge: that is an evaded hit, where no shove happened at all.
 */
@Mixin(LivingEntity.class)
public abstract class KnockbackReductionMixin {

    @ModifyVariable(method = "knockback", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private double tommemod$skillReduceBlockedKnockback(double strength) {
        double reduction = SkillDefenseBonuses.blockedKnockbackReduction((LivingEntity) (Object) this);
        return reduction <= 0.0 ? strength : strength * (1.0 - reduction);
    }
}
