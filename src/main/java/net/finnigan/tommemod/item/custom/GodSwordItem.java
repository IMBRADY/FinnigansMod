package net.finnigan.tommemod.item.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// A creative-mode toy: obliterates a huge box of terrain and anything living in front of the player.
// Not meant to be survival-obtainable - purely for testing/messing around.
public class GodSwordItem extends Item {

    private static final int RANGE = 60;
    private static final int HALF_WIDTH = 3;
    private static final int HALF_HEIGHT = 3;
    private static final float DAMAGE = 1000.0F;

    public GodSwordItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            return InteractionResultHolder.success(stack);
        }

        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 forward = player.getLookAngle().normalize();
        Vec3 worldUp = Math.abs(forward.y) > 0.99 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 right = forward.cross(worldUp).normalize();
        Vec3 up = right.cross(forward).normalize();

        Set<BlockPos> toBreak = new HashSet<>();
        for (int d = 1; d <= RANGE; d++) {
            Vec3 center = eye.add(forward.scale(d));
            for (int w = -HALF_WIDTH; w <= HALF_WIDTH; w++) {
                for (int h = -HALF_HEIGHT; h <= HALF_HEIGHT; h++) {
                    Vec3 point = center.add(right.scale(w)).add(up.scale(h));
                    toBreak.add(BlockPos.containing(point));
                }
            }
        }

        for (BlockPos pos : toBreak) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;
            if (state.getDestroySpeed(level, pos) < 0) continue; // leave bedrock/barriers etc. alone
            level.destroyBlock(pos, false, player);
        }

        AABB damageBox = new AABB(eye, eye.add(forward.scale(RANGE))).inflate(HALF_WIDTH + 1);
        List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, damageBox, e -> e != player);
        for (LivingEntity victim : victims) {
            victim.hurt(level.damageSources().playerAttack(player), DAMAGE);
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 4.0F, 0.7F);

        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Right-click to obliterate everything in front of you").withStyle(style -> style.withColor(0xFF5555)));
    }
}
