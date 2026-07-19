package net.finnigan.tommemod.item.custom.totems;

import net.finnigan.tommemod.item.custom.ITotemEffect;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

public class TotemOfThePhoenixItem extends Item implements ITotemEffect {
    public TotemOfThePhoenixItem(Properties properties) { super(properties); }

    @Override
    public void onPlayerTick(Player player, ItemStack totemStack) {
        if (!player.level().isClientSide) {
            player.clearFire();
        }
    }

    @Override
    public boolean onDamageTaken(Player player, ItemStack totemStack, DamageSource source, float amount) {
        return source.is(DamageTypes.ON_FIRE) || source.is(DamageTypes.IN_FIRE);
    }
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Immunity to fire. Don't try to swim in lava").withStyle(style -> style.withColor(0x9422AB)));
        tooltip.add(Component.literal("Accessory Item").withStyle(style -> style.withColor(0x5D156B)));
        // literal displays exactly as is, translatable grabs from json
    }
}