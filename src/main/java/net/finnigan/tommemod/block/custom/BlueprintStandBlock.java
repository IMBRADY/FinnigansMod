package net.finnigan.tommemod.block.custom;

import net.finnigan.tommemod.village.blueprint.BlueprintModeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Builder's job site, and the village's construction office: using it opens a screen showing
 * every building under way, from which the Chief enters blueprint mode (see BlueprintModeManager).
 * It has no block entity and no state of its own - the screen is fed by a packet - so it stays the
 * plain claimable POI the Builder profession needs.
 */
public class BlueprintStandBlock extends Block {

    public BlueprintStandBlock(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer serverPlayer) BlueprintModeManager.sendStandStatus(serverPlayer, pos, true);
        return InteractionResult.CONSUME;
    }
}
