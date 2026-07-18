package net.finnigan.tommemod.item.custom;

import net.finnigan.tommemod.entity.ModEntityTypes;
import net.finnigan.tommemod.entity.custom.GiantSwordEntity;
import net.finnigan.tommemod.item.custom.totems.TotemUtil;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.ToolActions;

public class InvertedSwordItem extends SwordItem {

    @Override
    public boolean isDamageable(ItemStack stack) {
        return false;
    }

    private static final int COOLDOWN_TICKS = 160; // ability cd
    private static final double SUMMON_HEIGHT = 30.0; // blocks above impact point

    public InvertedSwordItem(Tier tier, int attackDamage, float attackSpeed, Item.Properties properties) {
        super(tier, attackDamage, attackSpeed, properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }

        if (!level.isClientSide) {
            Vec3 targetPos = getTargetPosition(level, player);

            GiantSwordEntity giantSword = new GiantSwordEntity(ModEntityTypes.GIANT_SWORD.get(), level);
            giantSword.setPos(targetPos.x, targetPos.y + SUMMON_HEIGHT, targetPos.z);
            giantSword.setOwner(player);
            level.addFreshEntity(giantSword);

            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.5F, 0.7F);
        }

        player.getCooldowns().addCooldown(this, TotemUtil.applyCooldownReduction(player, COOLDOWN_TICKS));
        player.swing(hand);
        return InteractionResultHolder.success(stack);
    }

    // Raycasts along the player's look direction to find where the sword should land.
    private Vec3 getTargetPosition(Level level, Player player) {
        double reach = 20.0;
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();
        Vec3 endPos = eyePos.add(lookVec.scale(reach));

        BlockHitResult hit = level.clip(new ClipContext(
                eyePos, endPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));

        return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : endPos;
    }

    // ---- Multitool behavior ----

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        return 9.0F; // flat fast speed against everything; tune per-tier if you want scaling
    }

    @Override
    public boolean isCorrectToolForDrops(BlockState state) {
        return true; // always drops correctly, regardless of block
    }

    @Override
    public boolean canPerformAction(ItemStack stack, ToolAction toolAction) {
        if (toolAction == ToolActions.SHOVEL_FLATTEN || toolAction == ToolActions.HOE_TILL) {
            return false;
        }
        return true;
    }
}