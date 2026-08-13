package net.finnigan.tommemod.item.custom.totems;

import net.finnigan.tommemod.item.custom.ITotemEffect;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Hero of the Village for as long as the totem is worn - the trade discounts and villager gifts you
 * would normally only get for winning a raid. Refreshed on the totem tick (every 10 ticks) with a
 * duration comfortably longer than that, so it never visibly flickers.
 */
public class TotemOfTheHeroItem extends Item implements ITotemEffect {
    public TotemOfTheHeroItem(Properties properties) { super(properties); }

    @Override
    public void onPlayerTick(Player player, ItemStack totemStack) {
        if (player.level().isClientSide) return;
        player.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 220, 0, true, false));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Grants permanent Hero of the Village").withStyle(style -> style.withColor(0x9422AB)));
        tooltip.add(Component.literal("Accessory Item").withStyle(style -> style.withColor(0x5D156B)));
        // literal displays exactly as is, translatable grabs from json
    }
}
