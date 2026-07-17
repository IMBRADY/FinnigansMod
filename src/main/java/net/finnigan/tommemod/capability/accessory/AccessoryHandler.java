package net.finnigan.tommemod.capability.accessory;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

public class AccessoryHandler extends ItemStackHandler {

    public static final int SLOT_HEAD_ACCESSORY = 0; // banner or hat
    public static final int SLOT_ELYTRA = 1; // elytra
    public static final int SLOT_TOTEM_ACCESSORY = 2; // totems

    public AccessoryHandler() {
        super(3);
    }

    @Override
    public int getSlotLimit(int slot) {
        return 1;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        boolean result = switch (slot) {
            case SLOT_HEAD_ACCESSORY -> AccessoryItems.isHeadAccessory(stack);
            case SLOT_ELYTRA -> AccessoryItems.isElytraLike(stack);
            case SLOT_TOTEM_ACCESSORY -> AccessoryItems.isTotemAccessory(stack);
            default -> false;
        };
        com.mojang.logging.LogUtils.getLogger().info(
                "[tommemod] isItemValid check: slot={}, item={}, result={}", slot, stack.getItem(), result);
        return result;
    }

    @Override
    protected void onContentsChanged(int slot) {
        super.onContentsChanged(slot);
        // Sync hook is wired in AccessoryEvents / AccessoryProvider consumers
        if (changeListener != null) changeListener.run();
    }

    private Runnable changeListener;

    public void setChangeListener(Runnable listener) {
        this.changeListener = listener;
    }
}