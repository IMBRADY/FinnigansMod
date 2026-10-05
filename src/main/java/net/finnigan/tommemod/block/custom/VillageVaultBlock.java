package net.finnigan.tommemod.block.custom;

import net.finnigan.tommemod.village.buildings.VaultService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.UUID;

/**
 * The Bank's vault - the window onto the village's wealth (see VaultService). Holds nothing itself:
 * the bank lives on the village, so breaking a vault loses nothing and a new one opens the same bank.
 */
public class VillageVaultBlock extends Block {

    public VillageVaultBlock(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer serverPlayer) {
            UUID village = VaultService.villageFor(serverPlayer, pos);
            if (village != null) VaultService.sendStatus(serverPlayer, pos, village, true);
        }
        return InteractionResult.CONSUME;
    }
}
