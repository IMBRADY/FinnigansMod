package net.finnigan.tommemod.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.raid.Raid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Optional;

/**
 * Exposes where a raid's next wave is going to appear.
 *
 * Vanilla settles on the spot during the lull before the wave - up to fifteen seconds ahead of the
 * raiders themselves - and clears it again the instant they spawn, but never offers it to anyone.
 * That window is precisely the warning a defender needs, which is what
 * entity.custom.WarriorVillagerHelpers.HoldRaidLineGoal spends it on.
 */
@Mixin(Raid.class)
public interface RaidAccessor {

    @Accessor("waveSpawnPos")
    Optional<BlockPos> tommemod$getWaveSpawnPos();
}
