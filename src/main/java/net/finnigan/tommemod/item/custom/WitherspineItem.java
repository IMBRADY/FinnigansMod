package net.finnigan.tommemod.item.custom;

import net.finnigan.tommemod.entity.custom.WitherspineHelpers.WitherspineSkullEntity;
import net.finnigan.tommemod.event.UniqueSwordEnforcementHandler;
import net.finnigan.tommemod.network.ModNetwork;
import net.finnigan.tommemod.network.packet.SyncWitherspineStatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

public class WitherspineItem extends SwordItem {
    public static final int CHARGE_TICKS = 10 * 20;
    public static final int FIRING_TICKS = 10 * 20;
    public static final int USE_DURATION = CHARGE_TICKS + FIRING_TICKS;
    public static final String CHARGE_TICKS_TAG = "tommemod:witherspine_charge_ticks";
    public static final String FIRING_TICKS_LEFT_TAG = "tommemod:witherspine_firing_ticks_left";
    private static final int VOLLEY_INTERVAL_TICKS = 4;
    private static final String LAST_VOLLEY_TICK_TAG = "tommemod:witherspine_last_volley_tick";
    private static final String LAST_FIRING_INPUT_TICK_TAG = "tommemod:witherspine_last_firing_input_tick";

    public WitherspineItem(Tier tier, int attackDamage, float attackSpeed, Properties properties) {
        super(tier, attackDamage, attackSpeed, properties);
    }

    @Override
    public boolean isDamageable(ItemStack stack) {
        return false;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!UniqueSwordEnforcementHandler.canUseUniqueSword(player, this)) {
            return InteractionResultHolder.pass(stack);
        }

        int firingTicksLeft = player.getPersistentData().getInt(FIRING_TICKS_LEFT_TAG);
        if (firingTicksLeft > 0) {
            tickFiringPhase(level, player, firingTicksLeft);
            return InteractionResultHolder.consume(stack);
        }

        player.startUsingItem(hand);
        player.getPersistentData().remove(LAST_VOLLEY_TICK_TAG);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return USE_DURATION;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public void onUseTick(Level level, LivingEntity livingEntity, ItemStack stack,
                          int remainingUseDuration) {
        if (!(livingEntity instanceof Player player)) {
            return;
        }

        if (player.hurtTime > 0) {
            player.stopUsingItem();
            player.getPersistentData().remove(CHARGE_TICKS_TAG);
            player.getPersistentData().remove(LAST_VOLLEY_TICK_TAG);
            return;
        }

        int chargedTicks = player.getPersistentData().getInt(CHARGE_TICKS_TAG) + 1;
        if (chargedTicks < CHARGE_TICKS) {
            player.getPersistentData().putInt(CHARGE_TICKS_TAG, chargedTicks);
            syncState(player);
            return;
        }

        player.getPersistentData().remove(CHARGE_TICKS_TAG);
        player.getPersistentData().putInt(FIRING_TICKS_LEFT_TAG, FIRING_TICKS);
        player.getPersistentData().remove(LAST_FIRING_INPUT_TICK_TAG);
        syncState(player);
        player.stopUsingItem();
    }

    private void tickFiringPhase(Level level, Player player, int firingTicksLeft) {
        long now = level.getGameTime();
        long lastInputTick = player.getPersistentData().getLong(LAST_FIRING_INPUT_TICK_TAG);
        long inputGap = lastInputTick == 0L ? 1L : now - lastInputTick;
        int consumedTicks = inputGap > VOLLEY_INTERVAL_TICKS
                ? 1
                : (int) Math.max(1L, inputGap);
        player.getPersistentData().putLong(LAST_FIRING_INPUT_TICK_TAG, now);

        if (!level.isClientSide) {
            long lastVolley = player.getPersistentData().getLong(LAST_VOLLEY_TICK_TAG);
            if (lastVolley == 0L || now - lastVolley >= VOLLEY_INTERVAL_TICKS) {
                player.getPersistentData().putLong(LAST_VOLLEY_TICK_TAG, now);
                fireVolley(level, player);
            }
        }

        int remaining = firingTicksLeft - consumedTicks;
        if (remaining <= 0) {
            player.getPersistentData().remove(FIRING_TICKS_LEFT_TAG);
            player.getPersistentData().remove(LAST_VOLLEY_TICK_TAG);
            player.getPersistentData().remove(LAST_FIRING_INPUT_TICK_TAG);
            player.stopUsingItem();
        } else {
            player.getPersistentData().putInt(FIRING_TICKS_LEFT_TAG, remaining);
        }
        syncState(player);
    }

    private static void syncState(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        ModNetwork.CHANNEL.send(
                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> serverPlayer),
                new SyncWitherspineStatePacket(
                        player.getPersistentData().getInt(CHARGE_TICKS_TAG),
                        player.getPersistentData().getInt(FIRING_TICKS_LEFT_TAG)));
    }

    private void fireVolley(Level level, Player player) {
        for (float yawOffset : new float[]{-8.0F, 0.0F, 8.0F}) {
            WitherspineSkullEntity skull = new WitherspineSkullEntity(level, player);
            skull.shootFromRotation(player, player.getXRot(), player.getYRot() + yawOffset,
                    0.0F, 3.25F, 0.0F);
            level.addFreshEntity(skull);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 0.35F, 0.75F);
    }
}
