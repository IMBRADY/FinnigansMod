package net.finnigan.tommemod.block.entity;

import net.finnigan.tommemod.config.ModConfig;
import net.finnigan.tommemod.entity.custom.ElderVillagerEntity;
import net.finnigan.tommemod.entity.custom.WarriorVillagerEntity;
import net.finnigan.tommemod.menu.MonolithMenu;
import net.finnigan.tommemod.village.VillageManager;
import net.finnigan.tommemod.village.VillageRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads/displays live per-village data (population/defense counts, minimap markers, upgrade
 * levels). Deliberately holds none of that as real state of its own - every Monolith resolves its
 * own village from its position via VillageManager and reads the same shared data, which is what
 * makes multiple Monoliths in one village show identical info, and destroying one lose nothing.
 */
public class MonolithBlockEntity extends BlockEntity implements MenuProvider {

    public enum MarkerType { VILLAGER, IRON_GOLEM, ELDER, WARRIOR }

    public record Marker(int dx, int dz, MarkerType type) {
    }

    /** A claimed POI's position relative to this Monolith - a village's true footprint is the
     * union of each POI's small coverage radius, not one big circle, so the client traces the
     * boundary from these directly rather than from a single anchor+radius approximation. */
    public record PoiPoint(int dx, int dz) {
    }

    @Nullable
    private UUID villageId;
    private int openViewers = 0;
    private int scanCooldown = 0;

    private int ironGolemCount = 0;
    private int activeWarriorCount = 0;
    private int totalPopulation = 0;
    private int farmEfficiencyLevel = 0;
    private List<Marker> markers = new ArrayList<>();
    private List<PoiPoint> poiPoints = new ArrayList<>();

    public MonolithBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MONOLITH.get(), pos, state);
    }

    public void incrementViewers() {
        openViewers++;
    }

    public void decrementViewers() {
        openViewers = Math.max(0, openViewers - 1);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, MonolithBlockEntity be) {
        if (level.isClientSide()) return;
        if (be.openViewers <= 0) return;
        if (be.scanCooldown-- > 0) return;
        be.scanCooldown = Math.max(1, ModConfig.MONOLITH_REFRESH_INTERVAL_TICKS.get());
        be.refresh((ServerLevel) level);
    }

    /** Re-scans this Monolith's village and pushes the result to any tracking clients. Also called
     * immediately after an upgrade purchase, so the screen updates without waiting on the next tick. */
    public void refresh(ServerLevel level) {
        VillageManager manager = VillageManager.get(level);
        Optional<UUID> resolved = manager.resolveVillage(level, this.getBlockPos());

        if (resolved.isEmpty()) {
            villageId = null;
            ironGolemCount = 0;
            activeWarriorCount = 0;
            totalPopulation = 0;
            farmEfficiencyLevel = 0;
            markers.clear();
            poiPoints.clear();
            setChanged();
            return;
        }

        villageId = resolved.get();
        VillageRegion region = manager.resolveVillageRegion(level, villageId);
        BlockPos self = this.getBlockPos();
        farmEfficiencyLevel = manager.getFarmEfficiencyLevel(villageId);

        List<BlockPos> poiPositions = manager.getPoiPositions(villageId);
        poiPoints = new ArrayList<>(poiPositions.size());
        for (BlockPos poiPos : poiPositions) {
            poiPoints.add(new PoiPoint(poiPos.getX() - self.getX(), poiPos.getZ() - self.getZ()));
        }

        AABB box = new AABB(region.anchor()).inflate(region.radius());
        List<Villager> villagers = level.getEntitiesOfClass(Villager.class, box);
        List<IronGolem> golems = level.getEntitiesOfClass(IronGolem.class, box);
        List<ElderVillagerEntity> elders = level.getEntitiesOfClass(ElderVillagerEntity.class, box);
        List<WarriorVillagerEntity> warriors = level.getEntitiesOfClass(WarriorVillagerEntity.class, box).stream()
                .filter(WarriorVillagerEntity::isAlive)
                .toList();

        ironGolemCount = golems.size();
        activeWarriorCount = warriors.size();
        totalPopulation = villagers.size() + elders.size() + warriors.size();

        markers = new ArrayList<>(villagers.size() + golems.size() + elders.size() + warriors.size());
        for (Villager v : villagers) {
            markers.add(new Marker(v.getBlockX() - self.getX(), v.getBlockZ() - self.getZ(), MarkerType.VILLAGER));
        }
        for (IronGolem g : golems) {
            markers.add(new Marker(g.getBlockX() - self.getX(), g.getBlockZ() - self.getZ(), MarkerType.IRON_GOLEM));
        }
        for (ElderVillagerEntity e : elders) {
            markers.add(new Marker(e.getBlockX() - self.getX(), e.getBlockZ() - self.getZ(), MarkerType.ELDER));
        }
        for (WarriorVillagerEntity w : warriors) {
            markers.add(new Marker(w.getBlockX() - self.getX(), w.getBlockZ() - self.getZ(), MarkerType.WARRIOR));
        }

        setChanged();
    }

    public boolean hasVillage() {
        return villageId != null;
    }

    @Nullable
    public UUID getVillageId() {
        return villageId;
    }

    public int getIronGolemCount() {
        return ironGolemCount;
    }

    public int getActiveWarriorCount() {
        return activeWarriorCount;
    }

    public int getTotalPopulation() {
        return totalPopulation;
    }

    public int getFarmEfficiencyLevel() {
        return farmEfficiencyLevel;
    }

    public List<Marker> getMarkers() {
        return markers;
    }

    public List<PoiPoint> getPoiPoints() {
        return poiPoints;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.tommemod.monolith");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInv, Player player) {
        return new MonolithMenu(id, playerInv, this);
    }

    // ---- Persistence ----

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (villageId != null) tag.putUUID("VillageId", villageId);
        tag.putInt("IronGolemCount", ironGolemCount);
        tag.putInt("ActiveWarriorCount", activeWarriorCount);
        tag.putInt("TotalPopulation", totalPopulation);
        tag.putInt("FarmEfficiencyLevel", farmEfficiencyLevel);

        ListTag markerList = new ListTag();
        for (Marker marker : markers) {
            CompoundTag m = new CompoundTag();
            m.putInt("Dx", marker.dx());
            m.putInt("Dz", marker.dz());
            m.putString("Type", marker.type().name());
            markerList.add(m);
        }
        tag.put("Markers", markerList);

        ListTag poiList = new ListTag();
        for (PoiPoint poi : poiPoints) {
            CompoundTag p = new CompoundTag();
            p.putInt("Dx", poi.dx());
            p.putInt("Dz", poi.dz());
            poiList.add(p);
        }
        tag.put("PoiPoints", poiList);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        villageId = tag.hasUUID("VillageId") ? tag.getUUID("VillageId") : null;
        ironGolemCount = tag.getInt("IronGolemCount");
        activeWarriorCount = tag.getInt("ActiveWarriorCount");
        totalPopulation = tag.getInt("TotalPopulation");
        farmEfficiencyLevel = tag.getInt("FarmEfficiencyLevel");

        markers = new ArrayList<>();
        ListTag markerList = tag.getList("Markers", Tag.TAG_COMPOUND);
        for (int i = 0; i < markerList.size(); i++) {
            CompoundTag m = markerList.getCompound(i);
            markers.add(new Marker(m.getInt("Dx"), m.getInt("Dz"), MarkerType.valueOf(m.getString("Type"))));
        }

        poiPoints = new ArrayList<>();
        ListTag poiList = tag.getList("PoiPoints", Tag.TAG_COMPOUND);
        for (int i = 0; i < poiList.size(); i++) {
            CompoundTag p = poiList.getCompound(i);
            poiPoints.add(new PoiPoint(p.getInt("Dx"), p.getInt("Dz")));
        }
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
