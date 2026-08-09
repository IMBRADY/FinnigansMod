package net.finnigan.tommemod.item.custom.totems;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Lets the wearer walk across water. The movement itself is in
 * event.BeardedManWaterWalkHandler - it has to run every tick on both sides (the client simulates
 * the local player's motion), which ITotemEffect#onPlayerTick can't do: TotemEffectEvents only calls
 * that server-side, every 10 ticks.
 */
public class TotemOfTheBeardedManItem extends Item {
    public TotemOfTheBeardedManItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Walk on the surface of water. Sneak to sink").withStyle(style -> style.withColor(0x9422AB)));
        tooltip.add(Component.literal("Accessory Item").withStyle(style -> style.withColor(0x5D156B)));
        // literal displays exactly as is, translatable grabs from json
    }
}
