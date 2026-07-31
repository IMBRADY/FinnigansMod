package net.finnigan.tommemod.item.custom;

import net.finnigan.tommemod.event.UniqueSwordEnforcementHandler;
import net.finnigan.tommemod.item.custom.WarFlammerHelpers.FireWaveManager;
import net.finnigan.tommemod.item.custom.totems.TotemUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.level.Level;

/**
 * War Flammer: left click deals the base sword damage (25, handled automatically by vanilla SwordItem).
 * Right click fires "Fire Wave": a 4-step triangular fire wave fired forward, each step 1 step further
 * out and ~7 ticks (~0.33s) apart, damaging everything caught in it, igniting only the closest step's
 * targets, and launching hit enemies high into the air.
 * Passive: while in the Nether, 1.2x movement speed and 1.2x max HP; while holding, immune to fire/lava
 * damage and can move through lava at a strong speed (see WarFlammerPassiveHandler).
 */
public class WarFlammerItem extends SwordItem {

    private static final int COOLDOWN_TICKS = 120; // 6 seconds

    public WarFlammerItem(Tier tier, int attackDamage, float attackSpeed, Properties properties) {
        super(tier, attackDamage, attackSpeed, properties);
    }

    @Override
    public boolean isDamageable(ItemStack stack) {
        return false;
    }

    public static boolean isHeldBy(Player player) {
        return player.getMainHandItem().getItem() instanceof WarFlammerItem
                || player.getOffhandItem().getItem() instanceof WarFlammerItem;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.pass(stack);
        }
        if (!UniqueSwordEnforcementHandler.canUseUniqueSword(player, this)) {
            return InteractionResultHolder.pass(stack);
        }

        if (!level.isClientSide) {
            FireWaveManager.startWave((ServerLevel) level, player);

            player.getCooldowns().addCooldown(this, TotemUtil.applyCooldownReduction(player, COOLDOWN_TICKS));
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 1.2F, 0.9F);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0F, 1.1F);
        }

        return InteractionResultHolder.success(stack);
    }
}
