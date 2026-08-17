package net.finnigan.tommemod.mixin;

import net.finnigan.tommemod.skill.bonus.ModSkillBonuses;
import net.finnigan.tommemod.skill.bonus.SkillBonuses;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Excavation's Earthworks: soul sand and honey stop holding a digger up.
 *
 * These two slow you by a different route from a cobweb - not a multiplier written onto the entity but
 * a property of the block, read back every movement tick through {@code Entity.getBlockSpeedFactor}.
 * Hence a second mixin rather than an extension of {@link StuckSpeedMixin}: same symptom, different
 * mechanism, and the two nodes are deliberately kept to one mechanism each so neither quietly does the
 * other's job.
 */
@Mixin(Entity.class)
public abstract class BlockSpeedFactorMixin {

    @Inject(method = "getBlockSpeedFactor", at = @At("RETURN"), cancellable = true)
    private void tommemod$skillIgnoresLooseGround(CallbackInfoReturnable<Float> callback) {
        if (callback.getReturnValue() >= 1.0F) return;
        if (!((Object) this instanceof Player player)) return;

        if (SkillBonuses.has(player, ModSkillBonuses.LOOSE_GROUND_STRIDE)) {
            callback.setReturnValue(1.0F);
        }
    }
}
