package net.finnigan.tommemod.block.custom;

import net.finnigan.tommemod.block.entity.ChiefDeskBlockEntity;
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
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

/**
 * The village's command interface: Chief-only access to the tactical minimap and the village
 * upgrades, drawn from a GeckoLib model instead of a block model. Deliberately not a job site: the
 * Elder Villager claims Monoliths only, so a village that furnishes itself with Chief Desks never
 * ends up with rival job blocks (see MonolithBlockEntity#isElderJobSite). The Monolith itself is the
 * other half of that split - job site only, no screen.
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
                    Component.literal("Only the Village Chief may access the Chief Desk").withStyle(ChatFormatting.GRAY),
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
}
