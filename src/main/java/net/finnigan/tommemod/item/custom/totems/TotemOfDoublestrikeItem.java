package net.finnigan.tommemod.item.custom.totems;

import net.finnigan.tommemod.item.custom.ITotemEffect;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

public class TotemOfDoublestrikeItem extends Item implements ITotemEffect {

    private static final float DOUBLE_STRIKE_CHANCE = 0.15F;

    public TotemOfDoublestrikeItem(Properties properties) {
        super(properties);
    }

    /** Called from the attacker's perspective in LivingHurtEvent — see event hook below. */
    public boolean rollDoubleStrike(Player attacker) {
        return attacker.getRandom().nextFloat() < DOUBLE_STRIKE_CHANCE;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("15% chance to strike enemy twice").withStyle(style -> style.withColor(0x9422AB)));
        tooltip.add(Component.literal("Accessory Item").withStyle(style -> style.withColor(0x5D156B)));
        // literal displays exactly as is, translatable grabs from json
    }
}