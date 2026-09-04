package net.finnigan.tommemod.mixin;

import net.finnigan.tommemod.village.VillageSiegeSpawns;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.raid.Raid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes raid waves respect a village's walls.
 *
 * <p>Vanilla picks a wave's spawn point on a ring roughly 32-64 blocks out and only asks whether the
 * result is a village, using its own bell-and-bed notion of one. That notion knows nothing about the
 * wall a Chief just paid for, so a raid could - and regularly would - drop a wave straight into the
 * square behind it, which makes the whole upgrade decorative.
 *
 * <p>Hooked on findRandomSpawnPos rather than getValidSpawnPos because it is the one method both
 * routes to a spawn point run through: the lookahead that HoldRaidLineGoal reads, and the immediate
 * pick used when that lookahead came up empty. Correcting it here means both agree, so the Warriors
 * forming up are forming up facing where the wave will really appear.
 *
 * <p>The correction itself is {@link VillageSiegeSpawns}'s to make - this only asks.
 */
@Mixin(Raid.class)
public abstract class RaidWallSpawnMixin {

    @Inject(method = "findRandomSpawnPos", at = @At("RETURN"), cancellable = true)
    private void tommemod$keepWavesOutsideWalls(int p_37708_, int p_37709_, CallbackInfoReturnable<BlockPos> cir) {
        BlockPos proposed = cir.getReturnValue();
        // Null is vanilla giving up on this attempt; it retries, and there is nothing to correct.
        if (proposed == null) return;
        if (!(((Raid) (Object) this).getLevel() instanceof ServerLevel level)) return;

        BlockPos corrected = VillageSiegeSpawns.pushOutsideWalls(level, proposed);
        if (!corrected.equals(proposed)) cir.setReturnValue(corrected);
    }
}
