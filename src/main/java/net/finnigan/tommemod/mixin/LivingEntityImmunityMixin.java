package net.finnigan.tommemod.mixin;

import net.finnigan.tommemod.enchantment.ImmunityEffectReducer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityImmunityMixin {

    // Swapping the argument before addEffect looks at it is the only way to weaken an incoming effect
    // without re-entering addEffect: MobEffectEvent.Added hands over the instance too late to change it
    // (and exposes no setters), and cancelling via MobEffectEvent.Applicable to re-add a weaker copy
    // would recurse back through canBeAffected. Targets the two-arg overload, which the one-arg
    // version delegates to, so both entry points are covered.
    @ModifyVariable(
            method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"),
            argsOnly = true,
            index = 1)
    private MobEffectInstance tommemod$applyImmunity(MobEffectInstance instance) {
        return ImmunityEffectReducer.reduce((LivingEntity) (Object) this, instance);
    }
}
