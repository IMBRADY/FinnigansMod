package net.finnigan.tommemod.event;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.block.ModBlocks;
import net.finnigan.tommemod.block.custom.InvisibleLightBlock;
import net.finnigan.tommemod.enchantment.ModEnchantments;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Glow's light emission: the same invisible-light-block trick LumapierLightHandler uses, but driven
 * off the Glow enchantment and extended to dropped items.
 *
 * This one is level-ticked rather than player-ticked because ItemEntity has no Forge tick event of
 * its own - each server level sweeps its own players (helmet worn or held) plus any Glow-enchanted
 * item entities near them, then clears the tracked light for every source that is no longer glowing
 * in that level. Going through that "sources present this pass" set is what makes death, pickup,
 * despawn, logout and dimension change all clean up without a dedicated listener for each.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class GlowLightHandler {

    private static final int CHECK_INTERVAL_TICKS = 5;
    /** How far from a player we bother looking for glowing ground items - light beyond this is moot. */
    private static final double ITEM_SCAN_RADIUS = 32.0D;

    private static final Map<UUID, Placement> lastPlaced = new HashMap<>();

    private record Placement(ServerLevel level, BlockPos pos) {
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.level instanceof ServerLevel level)) return;
        if (level.getGameTime() % CHECK_INTERVAL_TICKS != 0) return;

        Map<UUID, Entity> sources = collectSources(level);

        for (Entity source : sources.values()) {
            updateLight(level, source);
        }

        // Anything we were lighting in this level that isn't a source anymore gets its block cleared.
        Iterator<Map.Entry<UUID, Placement>> it = lastPlaced.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Placement> entry = it.next();
            if (entry.getValue().level() != level) continue;
            if (sources.containsKey(entry.getKey())) continue;
            clearIfOurs(entry.getValue());
            it.remove();
        }
    }

    private static Map<UUID, Entity> collectSources(ServerLevel level) {
        Map<UUID, Entity> sources = new LinkedHashMap<>();
        for (ServerPlayer player : level.players()) {
            if (EnchantmentHelper.getEnchantmentLevel(ModEnchantments.GLOW.get(), player) > 0) {
                sources.putIfAbsent(player.getUUID(), player);
            }
            AABB box = player.getBoundingBox().inflate(ITEM_SCAN_RADIUS);
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box, GlowLightHandler::isGlowingItem)) {
                sources.putIfAbsent(item.getUUID(), item);
            }
        }
        return sources;
    }

    private static boolean isGlowingItem(ItemEntity item) {
        return item.isAlive()
                && EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.GLOW.get(), item.getItem()) > 0;
    }

    private static void updateLight(ServerLevel level, Entity source) {
        UUID id = source.getUUID();
        BlockPos current = source.blockPosition();
        Placement previous = lastPlaced.get(id);

        if (previous != null && previous.level() == level && previous.pos().equals(current)) {
            return; // already lit right here
        }

        if (previous != null) {
            clearIfOurs(previous);
            lastPlaced.remove(id);
        }

        if (!isSafeToPlace(level, current)) {
            // Something is already there - possibly another handler's light block. Leave it alone and
            // don't track a position we didn't place, or we'd clear someone else's light on the way out.
            return;
        }

        level.setBlockAndUpdate(current, ModBlocks.INVISIBLE_LIGHT.get().defaultBlockState());
        lastPlaced.put(id, new Placement(level, current));
    }

    /** Never overwrite anything real: only air or otherwise-replaceable blocks with no block entity. */
    private static boolean isSafeToPlace(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.isAir() && !state.canBeReplaced()) return false;
        return level.getBlockEntity(pos) == null;
    }

    /** Only clears the tracked position if it's still our invisible light block. */
    private static void clearIfOurs(Placement placement) {
        if (placement.level().getBlockState(placement.pos()).getBlock() instanceof InvisibleLightBlock) {
            placement.level().setBlockAndUpdate(placement.pos(), Blocks.AIR.defaultBlockState());
        }
    }
}
