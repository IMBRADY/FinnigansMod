package net.finnigan.tommemod.block.custom;

import net.finnigan.tommemod.block.entity.MonolithBlockEntity;
import net.minecraft.core.BlockPos;
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
 * Purely the Elder Villager's job site (see villager/ModPoiTypes.MONOLITH_POI). Right-clicking it
 * does nothing - the village screen (tactical minimap, upgrades) lives on the Chief Desk instead,
 * see {@link ChiefDeskBlock}. The block entity and its ticker are still needed here: they own the
 * POI ticket that makes the Monolith claimable, and the Elder promotion that follows.
 */
public class MonolithBlock extends Block implements EntityBlock {

    public MonolithBlock(Properties properties) {
        super(properties);
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
