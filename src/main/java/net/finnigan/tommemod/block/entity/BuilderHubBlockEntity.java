package net.finnigan.tommemod.block.entity;

import net.finnigan.tommemod.menu.BuilderHubMenu;
import net.finnigan.tommemod.village.VillageManager;
import net.finnigan.tommemod.village.VillageRegion;
import net.finnigan.tommemod.villager.ModVillagers;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

/**
 * Just a live snapshot of this Builder Hub's village (for the Chief check and the "Builder
 * Villagers present" count shown per building tile) - no ticking needed, refresh() is called
 * synchronously right before the menu opens (see BuilderHubBlock#use), same fix already applied
 * to the Monolith to avoid its stillValid seeing stale/null data right after open.
 */
public class BuilderHubBlockEntity extends BlockEntity implements MenuProvider {

    @Nullable
    private UUID villageId;
    private int builderVillagerCount = 0;

    public BuilderHubBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BUILDER_HUB.get(), pos, state);
    }

    public void refresh(ServerLevel level) {
        VillageManager manager = VillageManager.get(level);
        Optional<UUID> resolved = manager.resolveVillage(level, this.getBlockPos());

        if (resolved.isEmpty()) {
            villageId = null;
            builderVillagerCount = 0;
            setChanged();
            return;
        }

        villageId = resolved.get();
        VillageRegion region = manager.resolveVillageRegion(level, villageId);
        AABB box = new AABB(region.anchor()).inflate(region.radius());
        builderVillagerCount = (int) level.getEntitiesOfClass(Villager.class, box).stream()
                .filter(v -> v.getVillagerData().getProfession() == ModVillagers.BUILDER.get())
                .count();
        setChanged();
    }

    public boolean hasVillage() {
        return villageId != null;
    }

    @Nullable
    public UUID getVillageId() {
        return villageId;
    }

    public int getBuilderVillagerCount() {
        return builderVillagerCount;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.tommemod.builder_hub");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInv, Player player) {
        return new BuilderHubMenu(id, playerInv, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (villageId != null) tag.putUUID("VillageId", villageId);
        tag.putInt("BuilderVillagerCount", builderVillagerCount);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        villageId = tag.hasUUID("VillageId") ? tag.getUUID("VillageId") : null;
        builderVillagerCount = tag.getInt("BuilderVillagerCount");
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
