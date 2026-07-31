package net.finnigan.tommemod.block.custom;

import net.finnigan.tommemod.block.entity.BuilderHubBlockEntity;
import net.finnigan.tommemod.village.VillageManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

public class BuilderHubBlock extends Block implements EntityBlock {

    public BuilderHubBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BuilderHubBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                  InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        ServerLevel serverLevel = (ServerLevel) level;
        VillageManager manager = VillageManager.get(serverLevel);
        Optional<UUID> villageId = manager.resolveVillage(serverLevel, pos);
        boolean isChief = villageId.isPresent()
                && manager.getChief(villageId.get()).map(chief -> chief.equals(player.getUUID())).orElse(false);

        if (!isChief) {
            player.displayClientMessage(
                    Component.literal("Only the Village Chief may access the Builder Hub").withStyle(ChatFormatting.GRAY),
                    true);
            return InteractionResult.CONSUME;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof BuilderHubBlockEntity hub && player instanceof ServerPlayer serverPlayer) {
            hub.refresh(serverLevel);
            NetworkHooks.openScreen(serverPlayer, hub, pos);
        }
        return InteractionResult.CONSUME;
    }
}
