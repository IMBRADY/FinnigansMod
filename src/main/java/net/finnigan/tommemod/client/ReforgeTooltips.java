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
 * Spells out what an item's reforge is actually doing.
 *
 * <p>The reforge already shows in the item's name (see {@code ItemStackReforgeNameMixin}), but a name
 * only tells a player <em>which</em> reforge they have, not what it is worth. The line goes directly
 * under the name rather than at the end of the tooltip, where it would sit below the attribute block
 * whose numbers it explains.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ReforgeTooltips {

    private ReforgeTooltips() {
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        Reforge reforge = Reforges.get(event.getItemStack());
        if (reforge == null) return;

        Component line = Component.translatable(reforge.descKey())
                .withStyle(reforge.positive() ? ChatFormatting.GREEN : ChatFormatting.RED);

        // Index 0 is the item name, which is always present.
        event.getToolTip().add(Math.min(1, event.getToolTip().size()), line);
    }
}
