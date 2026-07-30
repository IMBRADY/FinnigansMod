package net.finnigan.tommemod.item.custom;

import net.finnigan.tommemod.event.RideStickManager;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

// Creative-mode toy. Right-click a mob (any range) to grab it (and its whole ride-stack); it then
// follows your crosshair at an adjustable distance until you right-click a different mob (making the
// held stack ride it) or right-click the held mob / empty space again (letting go where it floats).
public class RideStickItem extends Item {

    public RideStickItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) return InteractionResult.CONSUME;
        // The held mob floats directly in the crosshair, so a direct click on it isn't a real target -
        // fall back to the ray trace (which ignores the held mob) to find whatever is behind/beyond it.
        if (RideStickManager.isHeld(serverPlayer, target)) {
            resolveClick(serverPlayer);
        } else {
            RideStickManager.onClickEntity(serverPlayer, target);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) return InteractionResult.CONSUME;
        resolveClick(serverPlayer);
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            resolveClick(serverPlayer);
        }
        return InteractionResultHolder.consume(stack);
    }

    private void resolveClick(net.minecraft.server.level.ServerPlayer player) {
        LivingEntity target = RideStickManager.rayTraceEntity(player);
        if (target != null) {
            RideStickManager.onClickEntity(player, target);
        } else {
            RideStickManager.onClickNothing(player);
        }
    }
}
