package net.finnigan.tommemod.village.buildings;

import net.finnigan.tommemod.village.VillageFunds;
import net.finnigan.tommemod.village.VillageManager;
import net.finnigan.tommemod.village.blueprint.Blueprint;
import net.finnigan.tommemod.village.blueprint.BlueprintModeManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Knocking a finished blueprint building back down, from blueprint mode, with everything paid for it
 * handed back - into the bank if the village has one standing, otherwise to the Chief.
 *
 * <p>Only blocks that are still the ones the design put there are taken away: anything the player has
 * added or swapped in since is theirs and is left alone. Where the building's record says what stood
 * on its ground before (everything built since buildings started keeping that), the floor and the
 * foundation under it go back to exactly that; a building recorded without it gets plain dirt where
 * its floor was. Ground cleared to make room is left clear - nobody wants the hill back.
 *
 * <p>Chests and other containers in the building spill their contents rather than losing them.
 */
public final class BuildingDemolition {

    private BuildingDemolition() {
    }

    public static void demolish(ServerLevel level, VillageBuildings.Building building, ServerPlayer player) {
        Blueprint bp = building.blueprint();
        VillageBuildings data = VillageBuildings.get(level);
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

        if (bp != null) {
            Map<BlockPos, BlockState> design = new HashMap<>();
            for (Blueprint.Cell cell : bp.cells(building.rotation)) design.put(building.origin.offset(cell.pos()), cell.state());

            // Detail first and then top-down, so nothing is left hanging off a wall that has already gone.
            List<Map.Entry<BlockPos, BlockState>> order = new ArrayList<>(design.entrySet());
            order.sort(Comparator.<Map.Entry<BlockPos, BlockState>>comparingInt(e -> e.getValue().canOcclude() ? 1 : 0)
                    .thenComparing(e -> -e.getKey().getY()));

            for (Map.Entry<BlockPos, BlockState> e : order) {
                BlockPos pos = e.getKey();
                BlockState designed = e.getValue();
                if (designed.isAir()) continue;
                BlockState current = level.getBlockState(pos);
                // Stairs builders cut into the walkway to meet a lower neighbour count as the building's own.
                boolean ramp = building.originals.containsKey(pos) && current.is(Blocks.STONE_BRICK_STAIRS);
                if (!current.is(designed.getBlock()) && !ramp) continue;

                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof Container container) Containers.dropContents(level, pos, container);

                BlockState restored = building.originals.get(pos);
                if (restored == null) {
                    restored = pos.getY() == building.origin.getY() ? Blocks.DIRT.defaultBlockState() : Blocks.AIR.defaultBlockState();
                }
                level.setBlock(pos, restored, flags);
            }

            // Everything builders changed outside the design itself - the foundation poured into dips,
            // the stairs joining a wall's walkway to its neighbour's - goes back to what it was.
            for (Map.Entry<BlockPos, BlockState> e : building.originals.entrySet()) {
                BlockPos pos = e.getKey();
                if (design.containsKey(pos) || level.getBlockEntity(pos) != null) continue;
                if (!level.getBlockState(pos).equals(e.getValue())) level.setBlock(pos, e.getValue(), flags);
            }
        }

        data.remove(building.id);
        if (BuildingPurpose.WALL.equals(building.purpose)) WallPerimeter.recompute(level, building.villageId);

        refund(level, building, bp, player);

        double cx = building.bounds.getCenter().getX() + 0.5;
        double cz = building.bounds.getCenter().getZ() + 0.5;
        level.sendParticles(ParticleTypes.CLOUD, cx, building.bounds.minY() + 1.0, cz, 40,
                building.bounds.getXSpan() / 2.0, 1.0, building.bounds.getZSpan() / 2.0, 0.02);
        level.playSound(null, BlockPos.containing(cx, building.bounds.minY() + 1, cz), SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 1.2F, 0.8F);
        BlueprintModeManager.syncSites(level);
    }

    private static void refund(ServerLevel level, VillageBuildings.Building building, Blueprint bp, ServerPlayer player) {
        // A building recorded before payments were (or found by a survey) is refunded its design's price.
        List<Blueprint.Cost> refund = building.paid != null ? building.paid : bp != null ? bp.cost() : List.of();
        String name = building.displayName();
        if (refund.isEmpty()) {
            player.displayClientMessage(Component.literal("Demolished the " + name).withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        if (VillageFunds.bankActive(player, building.villageId)) {
            for (Blueprint.Cost c : refund) VillageManager.get(level).bankDeposit(building.villageId, c.item(), c.count());
            player.displayClientMessage(Component.literal("Demolished the " + name + " - materials returned to the bank")
                    .withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        for (Blueprint.Cost c : refund) {
            int remaining = c.count();
            while (remaining > 0) {
                int n = Math.min(remaining, c.item().getMaxStackSize());
                ItemStack stack = new ItemStack(c.item(), n);
                if (!player.getInventory().add(stack)) player.drop(stack, false);
                remaining -= n;
            }
        }
        player.displayClientMessage(Component.literal("Demolished the " + name + " - materials refunded")
                .withStyle(ChatFormatting.YELLOW), true);
    }
}
