package net.finnigan.tommemod.item.custom.totems;

import net.finnigan.tommemod.item.custom.ITotemEffect;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class TotemOfLuckyDiceItem extends Item implements ITotemEffect {

    private static final float DODGE_CHANCE = 0.1F;

    public TotemOfLuckyDiceItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean onDamageTaken(Player player, ItemStack totemStack, DamageSource source, float amount) {
        boolean dodged = player.getRandom().nextFloat() < DODGE_CHANCE;
        if (dodged) {
            playBlockEffect(player);
        }
        return dodged;
    }

    private void playBlockEffect(Player player) {
        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, player.blockPosition(),
                    SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.5F, 1.4F);

            serverLevel.sendParticles(ParticleTypes.CRIT,
                    player.getX(), player.getY() + player.getBbHeight() / 2, player.getZ(),
                    20, 0.4, 0.4, 0.4, 0.15);

            serverLevel.sendParticles(ParticleTypes.END_ROD,
                    player.getX(), player.getY() + player.getBbHeight() / 2, player.getZ(),
                    8, 0.3, 0.3, 0.3, 0.05);
        }
    }
}
