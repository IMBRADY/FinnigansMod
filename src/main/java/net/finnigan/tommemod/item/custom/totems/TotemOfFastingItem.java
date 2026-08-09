package net.finnigan.tommemod.item.custom.totems;

import net.finnigan.tommemod.effect.ModMobEffects;
import net.finnigan.tommemod.item.custom.ITotemEffect;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

public class TotemOfFastingItem extends Item implements ITotemEffect {

    /** Comfortably longer than TotemEffectEvents' 10-tick check so the buff never gaps between refreshes. */
    private static final int WELL_FED_DURATION_TICKS = 60;

    public TotemOfFastingItem(Properties properties) {
        super(properties);
    }

    @Override
    public void onPlayerTick(Player player, ItemStack totemStack) {
        if (player.level().isClientSide) return;

        if (player.getFoodData().getFoodLevel() >= 20) {
            player.addEffect(new MobEffectInstance(ModMobEffects.WELL_FED.get(),
                    WELL_FED_DURATION_TICKS, 0, true, false, true));
        } else {
            clearBuff(player);
        }
    }

    /** Also called from TotemEffectEvents when a *different* totem is equipped, so the buff can't outlive the totem. */
    public static void clearBuff(Player player) {
        if (player.hasEffect(ModMobEffects.WELL_FED.get())) {
            player.removeEffect(ModMobEffects.WELL_FED.get());
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("-35% saturation loss. When at full hunger, grants Well-Fed (+20% damage)").withStyle(style -> style.withColor(0x9422AB)));
        tooltip.add(Component.literal("Accessory Item").withStyle(style -> style.withColor(0x5D156B)));
        // literal displays exactly as is, translatable grabs from json
    }
}
