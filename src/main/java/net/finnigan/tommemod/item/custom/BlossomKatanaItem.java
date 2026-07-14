package net.finnigan.tommemod.item.custom;

import net.finnigan.tommemod.entity.custom.UndeadSwordHelpers.SoulSummoner;
import net.finnigan.tommemod.item.custom.BlossomKatanaHelpers.BlossomAuraEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.level.Level;

import java.util.List;

public class BlossomKatanaItem extends SwordItem {

    private static final int RADIUS = 10;
    private static final int DURATION_TICKS = 9 * 20;
    private static final int COOLDOWN_TICKS = 16 * 20;
    private static final String TAG_AURA_END_TIME = "AuraEndTime";


    public BlossomKatanaItem(Tier tier, int attackDamage, float attackSpeed, Properties properties) {
        super(tier, attackDamage, attackSpeed, properties);
    }

    @Override
    public boolean isDamageable(ItemStack stack) {
        return false;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.pass(stack);
        }

        if (!level.isClientSide) {
            triggerBlossomAura(level, player);
            player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);

            // Stamp the expiry time so the client can read it for the texture swap
            stack.getOrCreateTag().putLong(TAG_AURA_END_TIME, level.getGameTime() + DURATION_TICKS);
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.AZALEA_LEAVES_BREAK, SoundSource.PLAYERS, 1.7F, 1.4F);

        player.swing(hand);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    public static boolean isAuraActive(ItemStack stack, Level level) {
        if (level == null || !stack.hasTag()) return false;
        long endTime = stack.getOrCreateTag().getLong(TAG_AURA_END_TIME);
        return level.getGameTime() < endTime;
    }

    private void triggerBlossomAura(Level level, Player caster) {
        if (level instanceof ServerLevel serverLevel) {
            BlossomAuraEvents.spawnZone(serverLevel, caster.getX(), caster.getY(), caster.getZ(), RADIUS, DURATION_TICKS);
        }
    }

    private boolean isValidTarget(LivingEntity entity) {
        if (entity instanceof Player) {
            return true;
        }
        if (entity instanceof TamableAnimal tamable) {
            return tamable.isTame();
        }
        if (entity instanceof Villager) {
            return true;
        }
        if (entity.getTags().contains(SoulSummoner.SOUL_ALLY_TAG)) {
            return true;
        }
        return false;
    }

    private void applyBlossomBuffs(LivingEntity target) {
        target.addEffect(new MobEffectInstance(MobEffects.REGENERATION, DURATION_TICKS, 1, false, true, true));
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, DURATION_TICKS, 1, false, true, true));
        target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, DURATION_TICKS, 0, false, true, true));
    }
}