package net.finnigan.tommemod.block.custom;

import net.finnigan.tommemod.block.entity.ConstructionSiteBlockEntity;
import net.finnigan.tommemod.village.BuildingType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Placing this (given by the Builder Hub, tagged with a BuildingType + village) starts a
 * ConstructionSiteBlockEntity. It auto-removes itself with no drop on completion (can't be
 * re-collected), or refunds + demolishes the partial structure if broken first.
 */
public class ConstructionBannerBlock extends Block implements EntityBlock {

    public ConstructionBannerBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ConstructionSiteBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide() || !(level instanceof ServerLevel serverLevel)) return;
        if (!(level.getBlockEntity(pos) instanceof ConstructionSiteBlockEntity site)) return;

        CompoundTag tag = stack.getTag();
        BuildingType type = tag != null && tag.contains("BuildingType") ? parseType(tag.getString("BuildingType")) : null;
        UUID villageId = tag != null && tag.hasUUID("VillageId") ? tag.getUUID("VillageId") : null;
        Direction parsedFacing = tag != null && tag.contains("Facing") ? Direction.byName(tag.getString("Facing")) : null;
        Direction facing = parsedFacing != null ? parsedFacing : Direction.SOUTH;

        boolean ok = type != null && type.isImplemented() && villageId != null && site.initialize(serverLevel, type, villageId, facing);
        if (!ok) {
            serverLevel.removeBlock(pos, false);
            if (type != null && placer instanceof Player player) {
                refund(player, type);
                player.displayClientMessage(
                        Component.literal("Can't build here - ground is too uneven, obstructs a current building, or is too far from the village.")
                                .withStyle(ChatFormatting.RED),
                        true);
            }
        }
    }

    private static void refund(Player player, BuildingType type) {
        ItemStack emeralds = new ItemStack(Items.EMERALD, type.getEmeraldCost());
        ItemStack resources = new ItemStack(type.getResourceItem(), type.getResourceCount());
        if (!player.getInventory().add(emeralds)) player.drop(emeralds, false);
        if (!player.getInventory().add(resources)) player.drop(resources, false);
    }

    @Nullable
    private static BuildingType parseType(String name) {
        try {
            return BuildingType.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ConstructionSiteBlockEntity site) {
            site.refundAndDemolish(level, player);
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide()
                && level.getBlockEntity(pos) instanceof ConstructionSiteBlockEntity site) {
            // Covers non-player removals (explosions, fire, etc.); playerWillDestroy already
            // handled the player-break case above, and both methods guard via the BE's own
            // completed/demolished flags so this doesn't double-process either path.
            site.demolishOnly(level);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return (lvl, p, st, be) -> {
            if (be instanceof ConstructionSiteBlockEntity site) {
                ConstructionSiteBlockEntity.tick(lvl, p, st, site);
            }
        };
    }
}
