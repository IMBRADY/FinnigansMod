package net.finnigan.tommemod.block.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Invisible, non-solid, torch-level (14) light source, server-placed only by LumapierLightHandler as
 * the standard 1.20.1 "per-entity dynamic light" workaround. Deliberately has NO corresponding
 * BlockItem registration - it must never be player-placeable or obtainable, only ever placed/removed
 * programmatically by LumapierLightHandler.
 */
public class InvisibleLightBlock extends Block {

    public InvisibleLightBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return net.minecraft.world.phys.shapes.Shapes.empty();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return net.minecraft.world.phys.shapes.Shapes.empty();
    }
}
