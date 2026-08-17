package net.finnigan.tommemod.mixin;

import net.finnigan.tommemod.skill.event.SkillDefenseBonuses;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Defense's Warded: the blow that would have killed you leaves half a heart instead.
 *
 * Injected at the RETURN of {@code checkTotemDeathProtection} and only acting when it came back false,
 * which is exactly what "do not overlap the totem" requires. By that point vanilla has looked for a
 * Totem of Undying in either hand and {@link LivingEntityTotemMixin} has looked in the accessory slot;
 * if either found one it has already been spent and death is already prevented. Warded is what saves a
 * player who had none.
 *
 * Deliberately at RETURN rather than HEAD. At HEAD this would fire first and quietly make every totem
 * the player owns worthless, which is the overlap it was asked not to have.
 */
@Mixin(LivingEntity.class)
public abstract class LastBreathMixin {

    @Inject(method = "checkTotemDeathProtection", at = @At("RETURN"), cancellable = true)
    private void tommemod$skillLastBreath(DamageSource source, CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValue()) return; // a totem already answered for this
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;

        if ((Object) this instanceof Player player && SkillDefenseBonuses.tryLastBreath(player, source)) {
            callback.setReturnValue(true);
        }
    }
}
