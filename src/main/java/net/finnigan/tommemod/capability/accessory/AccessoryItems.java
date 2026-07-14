package net.finnigan.tommemod.capability.accessory;

import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.ItemStack;
import net.finnigan.tommemod.util.ModTags;

public class AccessoryItems {

    public static boolean isHeadAccessory(ItemStack stack) {
        return stack.is(ModTags.Items.HEAD_ACCESSORIES);
    }

    public static boolean isElytraLike(ItemStack stack) {
        return stack.getItem() instanceof ElytraItem || stack.is(ModTags.Items.ELYTRA_LIKE);
    }

    public static boolean isTotemAccessory(ItemStack stack) {
        return stack.is(ModTags.Items.TOTEM_ACCESSORIES);
    }
}