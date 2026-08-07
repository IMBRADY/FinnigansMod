package net.finnigan.tommemod.block.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Player-placeable invisible light source dropped by the lanternfly. Unlike
 * {@link InvisibleLightBlock} - Lumapier's server-only lighting hack - this one is a real, obtainable
 * block, so it deliberately keeps its full selection shape: with nothing to render and nothing to walk
 * into, the outline box is the only way to find it again and break it.
 */
public class GlowGooBlock extends Block {

    public GlowGooBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }
}
