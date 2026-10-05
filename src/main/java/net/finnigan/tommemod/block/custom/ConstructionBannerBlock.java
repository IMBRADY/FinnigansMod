package net.finnigan.tommemod.block.custom;

import net.finnigan.tommemod.block.entity.ConstructionSiteBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * Legacy: before blueprint mode, placing one of these started a self-building structure. It stays
 * registered so banners already in a world keep working (see ConstructionSiteBlockEntity), but a
 * newly placed one just hands itself back and points the player at the Blueprint Stand.
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
        if (level.isClientSide()) return;
        level.removeBlock(pos, false);
        if (placer instanceof Player player) {
            if (!player.getAbilities().instabuild) {
                ItemStack back = stack.copyWithCount(1);
                if (!player.getInventory().add(back)) player.drop(back, false);
            }
            player.displayClientMessage(Component.literal("Construction banners are retired - plan buildings at a Blueprint Stand")
                    .withStyle(ChatFormatting.GOLD), true);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide()
                && level.getBlockEntity(pos) instanceof ConstructionSiteBlockEntity site) {
            site.demolish(level);
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
