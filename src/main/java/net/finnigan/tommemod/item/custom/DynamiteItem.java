package net.finnigan.tommemod.item.custom;

import net.finnigan.tommemod.entity.custom.DynamiteEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class DynamiteItem extends Item {
    public DynamiteItem(Properties pProperties) {
        super(pProperties);
    }
    @Override
    public InteractionResultHolder<ItemStack> use(
            Level level,
            Player player,
            InteractionHand hand) {

        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide()) {

            DynamiteEntity dynamite =
                    new DynamiteEntity(level, player);

            dynamite.setItem(stack);

            dynamite.shootFromRotation(
                    player,
                    player.getXRot(),
                    player.getYRot(),
                    0.0F,
                    1.0F,   // velocity
                    1.0F    // inaccuracy
            );

            level.addFreshEntity(dynamite);
        }

        player.getCooldowns().addCooldown(this, 2);

        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }

        return InteractionResultHolder.sidedSuccess(
                stack,
                level.isClientSide());
    }

}
