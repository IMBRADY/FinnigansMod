package net.finnigan.tommemod.menu;

import net.finnigan.tommemod.block.entity.BuilderHubBlockEntity;
import net.finnigan.tommemod.village.VillageManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/** No inventory slots - purely a live data window onto BuilderHubBlockEntity, mirroring
 * MonolithMenu. Building purchases go through RequestBuildingBannerPacket. */
public class BuilderHubMenu extends AbstractContainerMenu {

    private final BuilderHubBlockEntity blockEntity;

    public BuilderHubMenu(int windowId, Inventory playerInventory, BuilderHubBlockEntity blockEntity) {
        super(ModMenuTypes.BUILDER_HUB_MENU.get(), windowId);
        this.blockEntity = blockEntity;
    }

    public BuilderHubBlockEntity getBlockEntity() {
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
}
