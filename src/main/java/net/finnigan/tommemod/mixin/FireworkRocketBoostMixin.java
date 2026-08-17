package net.finnigan.tommemod.mixin;

import net.finnigan.tommemod.skill.bonus.ModSkillBonuses;
import net.finnigan.tommemod.skill.bonus.SkillBonuses;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gliding's two firework nodes: Stormrider makes a rocket shove harder, Wing Care makes it shove for
 * longer. Both are the same item behaving differently in a trained flier's hands.
 *
 * A mixin rather than an event handler because neither thing vanilla does here is announced. The
 * shove is applied inline in the rocket's own tick, straight onto the flier's delta, and the rocket's
 * life is a private counter compared against a private limit - there is no hook on either.
 *
 * Injected at TAIL so vanilla's push has already landed and this adds to it, rather than racing it.
 * Both sides run it, which is deliberate: vanilla's own boost is applied on the client and the server
 * alike, so matching that is what keeps the extra in step with the flight it is amplifying.
 */
@Mixin(FireworkRocketEntity.class)
public abstract class FireworkRocketBoostMixin {

    @Shadow
    private int life;

    @Shadow
    private int lifetime;

    @Shadow
    @Nullable
    private LivingEntity attachedToEntity;

    /** The fraction of vanilla's own per-tick push that one point of Stormrider adds on top. */
    private static final double ROCKET_PUSH_SCALE = 0.1;

    @Inject(method = "tick", at = @At("TAIL"))
    private void tommemod$skillRocketBoost(CallbackInfo callback) {
        if (!(this.attachedToEntity instanceof Player pilot)) return;
        if (!pilot.isFallFlying()) return;

        // Both of these happen once, on the rocket's first tick. Lifetime especially: it is the number
        // life is compared against, so topping it up every tick would be a rocket that never burns out.
        if (this.life == 1) {
            double longer = SkillBonuses.get(pilot, ModSkillBonuses.ROCKET_DURATION);
            if (longer > 0.0) this.lifetime += (int) Math.round(this.lifetime * longer);

            // Skylord's rocket conservation. Handed back rather than not taken, because the item is
            // spent in FireworkRocketItem.use well before the rocket exists to be asked about it.
            if (!pilot.level().isClientSide()
                    && SkillBonuses.roll(pilot, ModSkillBonuses.ROCKET_CONSERVATION)) {
                ItemStack refund = new ItemStack(Items.FIREWORK_ROCKET);
                if (!pilot.getInventory().add(refund)) pilot.drop(refund, false);
            }
        }

        double harder = SkillBonuses.get(pilot, ModSkillBonuses.ROCKET_ACCELERATION);
        if (harder <= 0.0) return;

        Vec3 look = pilot.getLookAngle();
        pilot.setDeltaMovement(pilot.getDeltaMovement().add(look.scale(harder * ROCKET_PUSH_SCALE)));
    }
}
