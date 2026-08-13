package net.finnigan.tommemod.item.custom;

import net.finnigan.tommemod.entity.ModEntityTypes;
import net.finnigan.tommemod.entity.custom.BallistaEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Sets a Ballista down on the block you clicked. Placement is deliberately as strict as a block's:
 * it goes on top of an upward face, in a space nothing else occupies, and nowhere else - so one can
 * never be wedged into a wall or left hanging through a floor.
 */
public class BallistaItem extends Item {

    public BallistaItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getClickedFace() != Direction.UP) return InteractionResult.PASS;

        Level level = context.getLevel();
        BlockPos standingOn = context.getClickedPos().above();
        Player player = context.getPlayer();

        if (!hasRoomAt(level, standingOn)) {
            if (player != null && level.isClientSide()) {
                player.displayClientMessage(
                        Component.literal("Not enough room for a Ballista here").withStyle(ChatFormatting.GRAY),
                        true);
            }
            return InteractionResult.FAIL;
        }

        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.sidedSuccess(true);
        }

        BallistaEntity ballista = ModEntityTypes.BALLISTA.get().create(serverLevel);
        if (ballista == null) return InteractionResult.FAIL;

        // Squared to the face the player is standing on, then left there for good - the frame never
        // turns again, only the arm on top of it.
        float facing = player != null ? player.getYRot() : 0.0F;
        ballista.moveTo(standingOn.getX() + 0.5, standingOn.getY(), standingOn.getZ() + 0.5, facing, 0.0F);
        ballista.yBodyRot = facing;
        ballista.yHeadRot = facing;
        if (player != null) ballista.setOwnerUUID(player.getUUID());

        serverLevel.addFreshEntity(ballista);
        level.playSound(null, standingOn, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);

        if (player == null || !player.getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.CONSUME;
    }

    /** Whether a Ballista's own footprint fits at this position without overlapping anything. */
    private static boolean hasRoomAt(Level level, BlockPos pos) {
        AABB footprint = ModEntityTypes.BALLISTA.get()
                .getAABB(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        return level.noCollision(footprint) && level.getEntities(null, footprint).isEmpty();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Place on flat ground to build an emplacement")
                .withStyle(style -> style.withColor(0x9422AB)));
        tooltip.add(Component.literal("Shoots hostiles at extreme range")
                .withStyle(style -> style.withColor(0x9422AB)));
        tooltip.add(Component.literal("Sneak + right-click to take yours back down")
                .withStyle(style -> style.withColor(0x5D156B)));
    }
}
