package net.finnigan.tommemod.item.custom;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public interface ITotemEffect {
    default void onPlayerTick(Player player, ItemStack totemStack) {}

    default boolean onDamageTaken(Player player, ItemStack totemStack, DamageSource source, float amount) {
        return false;
    }
}