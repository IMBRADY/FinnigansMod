package net.finnigan.tommemod.block.entity;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.finnigan.tommemod.menu.ModMenuTypes;
import net.minecraftforge.items.SlotItemHandler;
import net.minecraftforge.items.IItemHandler;

public class OvenMenu extends AbstractContainerMenu {

    public final OvenBlockEntity blockEntity;
    private final Player player;

    public OvenMenu(int id, Inventory playerInv, OvenBlockEntity blockEntity) {
        super(ModMenuTypes.OVEN_MENU.get(), id);
        this.blockEntity = blockEntity;
        this.player = playerInv.player;

        IItemHandler handler = blockEntity.getItemHandler();

        // 4 input slots — single row, top-left, 16x16, 18px pitch
        for (int i = 0; i < 4; i++) {
            this.addSlot(new SlotItemHandler(handler, i, 29 + i * 18, 17));
        }

        // fuel slot — single 16x16 slot, below the inputs
        this.addSlot(new SlotItemHandler(handler, 8, 56, 53));

        // 4 output slots — 2x2 grid, 24x24 boxes, icon centered within each, non-insertable
        for (int i = 0; i < 4; i++) {
            int slotIndex = 4 + i;
            int col = i % 2;
            int row = i / 2;
            int x = 116 + col * 26;
            int y = 22 + row * 26;

            this.addSlot(new SlotItemHandler(handler, slotIndex, x, y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
        }

        // player inventory
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        // player hotbar
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInv, col, 8 + col * 18, 142));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot sourceSlot = slots.get(index);
        if (!sourceSlot.hasItem()) return ItemStack.EMPTY;
        ItemStack sourceStack = sourceSlot.getItem();
        ItemStack copy = sourceStack.copy();

        if (index < 9) {
            if (!this.moveItemStackTo(sourceStack, 9, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (!this.moveItemStackTo(sourceStack, 0, 4, false)) {
                if (!this.moveItemStackTo(sourceStack, 8, 9, false)) {
                    return ItemStack.EMPTY;
                }
            }
        }

        if (sourceStack.isEmpty()) sourceSlot.set(ItemStack.EMPTY);
        else sourceSlot.setChanged();

        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return blockEntity.getLevel() != null
                && player.distanceToSqr(blockEntity.getBlockPos().getX() + 0.5,
                blockEntity.getBlockPos().getY() + 0.5,
                blockEntity.getBlockPos().getZ() + 0.5) <= 64.0;
    }
}