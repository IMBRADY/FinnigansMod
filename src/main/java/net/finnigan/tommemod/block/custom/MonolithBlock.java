package net.finnigan.tommemod.block.custom;

import net.finnigan.tommemod.block.entity.MonolithBlockEntity;
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
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

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
                    Component.literal("Only the Village Chief may access the Monolith").withStyle(ChatFormatting.GRAY),
                    true);
            return InteractionResult.CONSUME;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MonolithBlockEntity monolith && player instanceof ServerPlayer serverPlayer) {
            // Populate village data synchronously - otherwise it stays null until the block
            // entity's own throttled ticker next fires, and MonolithMenu.stillValid() would see
            // that null village and immediately close the screen right after it opens.
            monolith.refresh(serverLevel);
            NetworkHooks.openScreen(serverPlayer, monolith, pos);
        }
        return InteractionResult.CONSUME;
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
