package net.finnigan.tommemod.menu;

import net.finnigan.tommemod.entity.custom.WarriorVillagerEntity;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

/**
 * Chief-only equipment editor for a WarriorVillagerEntity. Slots read/write the entity's live
 * equipment array directly (via a per-slot Container adapter) rather than a separate synced
 * inventory, so the menu always reflects exactly what's equipped/rendered.
 */
public class WarriorVillagerMenu extends AbstractContainerMenu {

    private static final int EQUIPMENT_SLOT_COUNT = 5;

    private final WarriorVillagerEntity entity;

    public WarriorVillagerMenu(int windowId, Inventory playerInventory, WarriorVillagerEntity entity) {
        super(ModMenuTypes.WARRIOR_VILLAGER_MENU.get(), windowId);
        this.entity = entity;

        this.addSlot(new EquipmentSlotUi(entity, EquipmentSlot.MAINHAND, 8, 18));
        this.addSlot(new EquipmentSlotUi(entity, EquipmentSlot.HEAD, 8, 36));
        this.addSlot(new EquipmentSlotUi(entity, EquipmentSlot.CHEST, 8, 54));
        this.addSlot(new EquipmentSlotUi(entity, EquipmentSlot.LEGS, 8, 72));
        this.addSlot(new EquipmentSlotUi(entity, EquipmentSlot.FEET, 8, 90));

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 122 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 180));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot sourceSlot = slots.get(index);
        if (!sourceSlot.hasItem()) return ItemStack.EMPTY;
        ItemStack sourceStack = sourceSlot.getItem();
        ItemStack copy = sourceStack.copy();

        if (index < EQUIPMENT_SLOT_COUNT) {
            if (!this.moveItemStackTo(sourceStack, EQUIPMENT_SLOT_COUNT, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (!this.moveItemStackTo(sourceStack, 0, EQUIPMENT_SLOT_COUNT, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (sourceStack.isEmpty()) sourceSlot.set(ItemStack.EMPTY);
        else sourceSlot.setChanged();

        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return entity.isAlive()
                && player.distanceToSqr(entity) <= 64.0
                && player.getUUID().equals(net.finnigan.tommemod.village.VillageManager
                        .get((net.minecraft.server.level.ServerLevel) entity.level())
                        .getChief(entity.getVillageId())
                        .orElse(null));
    }

    /** Only restricts placement (mayPlace); everything else routes through EquipmentContainer. */
    private static class EquipmentSlotUi extends Slot {
        private final EquipmentSlot equipmentSlot;

        EquipmentSlotUi(WarriorVillagerEntity entity, EquipmentSlot equipmentSlot, int x, int y) {
            super(new EquipmentContainer(entity, equipmentSlot), 0, x, y);
            this.equipmentSlot = equipmentSlot;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (equipmentSlot == EquipmentSlot.MAINHAND) return true;
            return stack.getItem() instanceof ArmorItem armor && armor.getEquipmentSlot() == equipmentSlot;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    /** Adapts a single WarriorVillagerEntity equipment slot to the Container interface Slot expects. */
    private static class EquipmentContainer implements Container {
        private final WarriorVillagerEntity entity;
        private final EquipmentSlot slot;

        EquipmentContainer(WarriorVillagerEntity entity, EquipmentSlot slot) {
            this.entity = entity;
            this.slot = slot;
        }

        @Override
        public int getContainerSize() {
            return 1;
        }

        @Override
        public boolean isEmpty() {
            return entity.getItemBySlot(slot).isEmpty();
        }

        @Override
        public ItemStack getItem(int index) {
            return entity.getItemBySlot(slot);
        }

        @Override
        public ItemStack removeItem(int index, int count) {
            ItemStack stack = entity.getItemBySlot(slot);
            ItemStack split = stack.split(count);
            entity.setItemSlot(slot, stack);
            return split;
        }

        @Override
        public ItemStack removeItemNoUpdate(int index) {
            ItemStack stack = entity.getItemBySlot(slot);
            entity.setItemSlot(slot, ItemStack.EMPTY);
            return stack;
        }

        @Override
        public void setItem(int index, ItemStack stack) {
            entity.setItemSlot(slot, stack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public void setChanged() {
        }

        @Override
        public boolean stillValid(Player player) {
            return entity.isAlive();
        }

        @Override
        public void clearContent() {
            entity.setItemSlot(slot, ItemStack.EMPTY);
        }
    }
}
