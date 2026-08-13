package net.finnigan.tommemod.item.custom.totems;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Teaches the wearer to throw fire charges as fireballs instead of only being able to place fire
 * with them. The throwing itself is in event.FireMagicTotemHandler - the totem sits in the accessory
 * slot, so the fire charge in hand is what the player actually right-clicks.
 */
public class TotemOfFireMagicItem extends Item {
    public TotemOfFireMagicItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Right-click a Fire Charge to hurl it as a fireball").withStyle(style -> style.withColor(0x9422AB)));
        tooltip.add(Component.literal("Bursts on impact and sets the ground alight").withStyle(style -> style.withColor(0x9422AB)));
        tooltip.add(Component.literal("Aim at a block in reach to place fire as normal").withStyle(style -> style.withColor(0x9422AB)));
        tooltip.add(Component.literal("Accessory Item").withStyle(style -> style.withColor(0x5D156B)));
        // literal displays exactly as is, translatable grabs from json
    }
}
