package net.finnigan.tommemod.menu;

import net.finnigan.tommemod.block.entity.MonolithBlockEntity;
import net.finnigan.tommemod.village.VillageManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * No inventory slots - purely a live data window onto MonolithBlockEntity (population/defense
 * counts, minimap markers, upgrade levels). Upgrade purchases go through MonolithUpgradePacket
 * rather than slot interactions.
 */
public class MonolithMenu extends AbstractContainerMenu {

    private final MonolithBlockEntity blockEntity;

    public MonolithMenu(int windowId, Inventory playerInventory, MonolithBlockEntity blockEntity) {
        super(ModMenuTypes.MONOLITH_MENU.get(), windowId);
        this.blockEntity = blockEntity;
        blockEntity.incrementViewers();
    }

    public MonolithBlockEntity getBlockEntity() {
        return blockEntity;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        if (blockEntity.getVillageId() == null) return false;
        return player.getUUID().equals(VillageManager.get((ServerLevel) player.level())
                .getChief(blockEntity.getVillageId()).orElse(null));
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        blockEntity.decrementViewers();
    }
}
