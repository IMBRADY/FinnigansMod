package net.finnigan.tommemod.village.buildings;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.config.ModConfig;
import net.finnigan.tommemod.village.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraftforge.event.VanillaGameEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Farm Plots feed the village bank: each crop a Farmer replants on a plot counts towards the
 * village's wealth - every {@code farmReplantsPerEmerald} replants adds an emerald to the bank - but
 * only while the village has a Bank standing to put it in.
 *
 * <p>Getting Farmers onto the plots and working them is {@link FarmerWorkHandler}.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public final class FarmPlotHandler {

    private static final Map<UUID, Integer> REPLANTS = new HashMap<>();

    private FarmPlotHandler() {
    }

    /** A Farmer replanting on a plot - every planting is reported as a block-place event. */
    @SubscribeEvent
    public static void onGameEvent(VanillaGameEvent event) {
        if (event.getVanillaEvent() != GameEvent.BLOCK_PLACE) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getCause() instanceof Villager v) || v.getVillagerData().getProfession() != VillagerProfession.FARMER) return;
        BlockState placed = event.getContext().affectedState();
        if (placed == null || !placed.is(BlockTags.CROPS)) return;

        BlockPos pos = BlockPos.containing(event.getEventPosition());
        VillageBuildings data = VillageBuildings.get(level);
        VillageBuildings.Building plot = data.standingAt(level, pos, BuildingPurpose.FARM);
        if (plot == null || !data.hasBank(level, plot.villageId)) return;

        int count = REPLANTS.merge(plot.villageId, 1, Integer::sum);
        if (count >= ModConfig.FARM_REPLANTS_PER_EMERALD.get()) {
            REPLANTS.put(plot.villageId, 0);
            VillageManager.get(level).bankDeposit(plot.villageId, Items.EMERALD, 1);
        }
    }
}
