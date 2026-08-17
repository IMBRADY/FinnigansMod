package net.finnigan.tommemod.mixin;

import net.finnigan.tommemod.skill.event.SkillMovementBonuses;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Agility's Marathon: cobwebs, honey and powder snow stop dragging on a trained runner.
 *
 * A mixin because the drag is neither an attribute nor an event. A block that slows you calls
 * {@code Entity.makeStuckInBlock} to write a multiplier straight onto the entity, which the next
 * movement tick reads and applies - there is nothing in between to hook, and no attribute to counter.
 * Declining the write is the only place the effect can be refused.
 *
 * Both sides run it, because a player's movement is simulated on their own client and confirmed by the
 * server; refusing on one and not the other is what rubber-banding is made of.
 */
@Mixin(Entity.class)
public abstract class StuckSpeedMixin {

    @Inject(method = "makeStuckInBlock", at = @At("HEAD"), cancellable = true)
    private void tommemod$skillIgnoresStickyGround(net.minecraft.world.level.block.state.BlockState state,
                                                   Vec3 multiplier, CallbackInfo callback) {
        if ((Object) this instanceof Player player && SkillMovementBonuses.ignoresStickyGround(player)) {
            callback.cancel();
        }
    }
}
