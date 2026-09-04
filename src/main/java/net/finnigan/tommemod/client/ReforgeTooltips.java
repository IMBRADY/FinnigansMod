package net.finnigan.tommemod.client;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.reforge.Reforge;
import net.finnigan.tommemod.reforge.Reforges;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Says which reforge an item rolled and what that reforge is actually doing.
 *
 * <p>This line is now the <em>only</em> place a reforge shows. A reforge used to also prefix the item's
 * name ("Hasty Iron Sword"), which meant every rolled item read as a different item than the one the
 * player crafted; that prefix is gone, so the name has to carry here instead of just the effect.
 *
 * <p>The line goes directly under the name rather than at the end of the tooltip, where it would sit
 * below the attribute block whose numbers it explains.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ReforgeTooltips {

    private ReforgeTooltips() {
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        Reforge reforge = Reforges.get(event.getItemStack());
        if (reforge == null) return;

        Component line = Component.translatable(reforge.nameKey())
                .append(": ")
                .append(Component.translatable(reforge.descKey()))
                .withStyle(reforge.positive() ? ChatFormatting.GREEN : ChatFormatting.RED);

        // Index 0 is the item name, which is always present.
        event.getToolTip().add(Math.min(1, event.getToolTip().size()), line);
    }
}
