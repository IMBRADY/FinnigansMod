package net.finnigan.tommemod.event.VillageUpgradeEventHelpers;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.config.ModConfig;
import net.finnigan.tommemod.village.VillageManager;
import net.finnigan.tommemod.village.VillageRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Applies the Monolith's Farm Efficiency village upgrade: crops in the village grow faster.
 * Rather than scanning every block in a (potentially huge) village region every pass, each
 * throttled pass samples a small number of random columns within the village and, for any crop
 * found there, rolls the upgrade's chance to grant it one extra vanilla growth tick.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class FarmEfficiencyTickHandler {

    private static final int SAMPLES_PER_PASS = 20;
    private static final Map<UUID, Integer> tickCounters = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        int interval = Math.max(1, ModConfig.FARM_EFFICIENCY_TICK_INTERVAL_TICKS.get());
        int counter = tickCounters.merge(player.getUUID(), 1, Integer::sum);
        if (counter < interval) return;
        tickCounters.put(player.getUUID(), 0);

        VillageManager manager = VillageManager.get(level);
        Optional<UUID> villageId = manager.resolveVillage(level, player.blockPosition());
        if (villageId.isEmpty()) return;

        int farmLevel = manager.getFarmEfficiencyLevel(villageId.get());
        if (farmLevel <= 0) return;

        double chance = farmLevel * ModConfig.FARM_EFFICIENCY_PERCENT_PER_LEVEL.get();
        VillageRegion region = manager.resolveVillageRegion(level, villageId.get());
        int radius = (int) Math.round(region.radius());
        if (radius <= 0) return;

        RandomSource random = level.getRandom();
        BlockPos anchor = region.anchor();

        for (int i = 0; i < SAMPLES_PER_PASS; i++) {
            int worldX = anchor.getX() + random.nextInt(radius * 2 + 1) - radius;
            int worldZ = anchor.getZ() + random.nextInt(radius * 2 + 1) - radius;
            if (!level.hasChunk(worldX >> 4, worldZ >> 4)) continue;

            int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, worldX, worldZ) - 1;
            BlockPos pos = new BlockPos(worldX, surfaceY, worldZ);
            BlockState state = level.getBlockState(pos);

            if (state.getBlock() instanceof CropBlock crop && random.nextDouble() < chance) {
                crop.randomTick(state, level, pos, random);
            }
        }
    }
}
