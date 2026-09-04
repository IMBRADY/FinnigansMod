package net.finnigan.tommemod.block.custom;

import net.finnigan.tommemod.block.entity.MonolithBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

import javax.annotation.Nullable;

/**
 * Purely the Elder Villager's job site (see villager/ModPoiTypes.MONOLITH_POI). Right-clicking it
 * does nothing - the village screen (tactical minimap, upgrades) lives on the Chief Desk instead,
 * see {@link ChiefDeskBlock}. The block entity and its ticker are still needed here: they own the
 * POI ticket that makes the Monolith claimable, and the Elder promotion that follows.
 *
 * <p>Horizontally directional, which {@link ChiefDeskBlock} inherits. Note that MONOLITH_POI is built
 * from {@code getPossibleStates()}, so all four facings register as the POI without further work.
 */
public class MonolithBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public MonolithBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /**
     * Turned to face whoever placed it, the way a furnace or a chest does - hence the {@code
     * getOpposite()}. {@code getHorizontalDirection()} is the direction the player is *looking*, so
     * using it raw would point the block's front away from them.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MonolithBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return (lvl, p, st, be) -> {
            if (be instanceof MonolithBlockEntity monolith) {
                MonolithBlockEntity.tick(lvl, p, st, monolith);
            }
        };
    }
}
