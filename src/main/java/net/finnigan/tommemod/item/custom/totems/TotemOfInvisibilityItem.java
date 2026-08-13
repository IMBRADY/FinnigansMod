package net.finnigan.tommemod.item.custom.totems;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Turns the wearer invisible, but only after a warm-up (see
 * event.InvisibilityTotemHandler for the timing and for the instant drop on unequip). The whole
 * point of the delay is that swapping the totem in and out can't be used as a free blink - you have
 * to commit to wearing it.
 *
 * Deliberately not an ITotemEffect: that only ticks every 10 ticks, which is too coarse both for
 * counting the warm-up and for taking the invisibility away the moment the totem comes off.
 */
public class TotemOfInvisibilityItem extends Item {
    public TotemOfInvisibilityItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Turns you invisible after 4 seconds of wear").withStyle(style -> style.withColor(0x9422AB)));
        tooltip.add(Component.literal("Wears off the instant it is removed").withStyle(style -> style.withColor(0x9422AB)));
        tooltip.add(Component.literal("Accessory Item").withStyle(style -> style.withColor(0x5D156B)));
        // literal displays exactly as is, translatable grabs from json
    }
}
