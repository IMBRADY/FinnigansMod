package net.finnigan.tommemod.event.LumapierEventHelpers;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.block.ModBlocks;
import net.finnigan.tommemod.block.custom.InvisibleLightBlock;
import net.finnigan.tommemod.item.custom.LumapierItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Lumapier's light emission (Phase 5c): while holding, place an invisible torch-level light block at
 * the player's feet, tracked per player UUID so the previous spot is reliably cleared back to air on
 * every relevant transition (movement, unequip, death, logout, dimension change). A leaked/orphaned
 * light block is the one failure mode to avoid here, so every path that can end tracking goes through
 * clearTracked/clearIfOurs, and clearIfOurs re-checks the block is still ours before touching it (in
 * case something else was placed there in the meantime).
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class LumapierLightHandler {

    private static final int CHECK_INTERVAL_TICKS = 5;

    private static final Map<UUID, Placement> lastPlaced = new HashMap<>();

    private static class Placement {
        final ServerLevel level;
        final BlockPos pos;

        Placement(ServerLevel level, BlockPos pos) {
            this.level = level;
            this.pos = pos;
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide) return;
        if (!(player.level() instanceof ServerLevel serverLevel)) return;
        if (player.tickCount % CHECK_INTERVAL_TICKS != 0) return;

        UUID id = player.getUUID();

        if (!LumapierItem.isHeldBy(player)) {
            clearTracked(id);
            return;
        }

        BlockPos current = player.blockPosition();
        Placement previous = lastPlaced.get(id);

        if (previous != null && previous.level == serverLevel && previous.pos.equals(current)) {
            return; // already placed right here, nothing to do
        }

        if (previous != null) {
            clearIfOurs(previous.level, previous.pos);
            lastPlaced.remove(id);
        }

        if (!isSafeToPlace(serverLevel, current)) {
            return;
        }

        serverLevel.setBlockAndUpdate(current, ModBlocks.INVISIBLE_LIGHT.get().defaultBlockState());
        lastPlaced.put(id, new Placement(serverLevel, current));
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        clearTracked(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        clearTracked(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        clearTracked(player.getUUID());
    }

    /** Never overwrite anything real: only air or otherwise-replaceable blocks with no block entity. */
    private static boolean isSafeToPlace(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.isAir() && !state.canBeReplaced()) return false;
        return level.getBlockEntity(pos) == null;
    }

    private static void clearTracked(UUID id) {
        Placement placement = lastPlaced.remove(id);
        if (placement == null) return;
        clearIfOurs(placement.level, placement.pos);
    }

    /** Only clears the tracked position if it's still our invisible light block - never clobbers
     * something else that may have been placed there in the meantime. */
    private static void clearIfOurs(ServerLevel level, BlockPos pos) {
        if (level.getBlockState(pos).getBlock() instanceof InvisibleLightBlock) {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
    }
}
