package net.finnigan.tommemod.block.custom;

import net.finnigan.tommemod.block.entity.ChiefDeskBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * The Monolith's twin as far as players are concerned - same Chief-only access, same village
 * screen, same upgrades - drawn from a GeckoLib model instead of a block model. Deliberately not a
 * job site: the Elder Villager claims Monoliths only, so a village that furnishes itself with Chief
 * Desks never ends up with rival job blocks (see MonolithBlockEntity#isElderJobSite).
 */
public class ChiefDeskBlock extends MonolithBlock {

    public ChiefDeskBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ChiefDeskBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED; // drawn by ChiefDeskRenderer, not from a block model
    }
}
