package net.finnigan.tommemod.block.entity;

import net.finnigan.tommemod.config.ModConfig;
import net.finnigan.tommemod.entity.custom.ElderVillagerEntity;
import net.finnigan.tommemod.entity.custom.WarriorVillagerEntity;
import net.finnigan.tommemod.menu.MonolithMenu;
import net.finnigan.tommemod.village.ElderPromotion;
import net.finnigan.tommemod.village.VillageManager;
import net.finnigan.tommemod.village.VillageRegion;
import net.finnigan.tommemod.village.VillageUpgrade;
import net.finnigan.tommemod.villager.ModPoiTypes;
import net.finnigan.tommemod.villager.ModVillagers;
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
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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

    /** How often the job site's availability is re-evaluated. Cheap, but not free - it counts the
     * village's Villagers - so it runs on its own slow cadence rather than every tick. */
    private static final int RECRUIT_INTERVAL_TICKS = 100;
    /** How often we look for a Villager that has arrived and claimed us. */
    private static final int PROMOTION_CHECK_INTERVAL_TICKS = 5;
    /** AssignProfessionFromJobSite fires within 2 blocks of the job site; a little slack on top. */
    private static final double PROMOTION_SEARCH_RADIUS = 3.0D;

    @Nullable
    private UUID villageId;
    private int openViewers = 0;
    private int scanCooldown = 0;
    private int recruitCooldown = 0;
    private int promotionCooldown = 0;
    /** Whether this Monolith is the one currently sitting on its own POI ticket, deliberately making
     * itself unclaimable. Persisted so a reload doesn't lose track of a ticket we're responsible for
     * releasing - releasing one we never took would strip it from a Villager on its way over. */
    private boolean holdsOwnJobSite = false;

    private int ironGolemCount = 0;
    private int activeWarriorCount = 0;
    private int totalPopulation = 0;
    /** Every Chief Desk upgrade's current level, mirrored from VillageManager for the screen. */
    private final Map<VillageUpgrade, Integer> upgradeLevels = new EnumMap<>(VillageUpgrade.class);
    private List<Marker> markers = new ArrayList<>();
    private List<PoiPoint> poiPoints = new ArrayList<>();

    public MonolithBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.MONOLITH.get(), pos, state);
    }

    protected MonolithBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /**
     * Whether this block is the village's Elder job site. True for the Monolith itself; the Chief
     * Desk shares everything else about a Monolith but is purely furniture as far as the Elder is
     * concerned, so two job blocks never end up competing inside one village.
     */
    protected boolean isElderJobSite() {
        return true;
    }

    public void incrementViewers() {
        openViewers++;
    }

    public void decrementViewers() {
        openViewers = Math.max(0, openViewers - 1);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, MonolithBlockEntity be) {
        if (level.isClientSide()) return;
        ServerLevel serverLevel = (ServerLevel) level;

        if (be.isElderJobSite()) {
            if (--be.promotionCooldown <= 0) {
                be.promotionCooldown = PROMOTION_CHECK_INTERVAL_TICKS;
                be.promoteArrivedVillager(serverLevel);
            }
            if (--be.recruitCooldown <= 0) {
                be.recruitCooldown = RECRUIT_INTERVAL_TICKS;
                be.updateJobSiteAvailability(serverLevel);
            }
        }

        if (be.openViewers <= 0) return;
        if (be.scanCooldown-- > 0) return;
        be.scanCooldown = Math.max(1, ModConfig.MONOLITH_REFRESH_INTERVAL_TICKS.get());
        be.refresh(serverLevel);
    }

    // ---- Elder job site ----

    /**
     * Opens or closes this Monolith as a claimable job block, by holding its own POI ticket when it
     * shouldn't be claimable. Doing it this way rather than gating the promotion itself means a
     * Villager is never sent on a pointless walk: vanilla only routes them to job sites with a free
     * ticket, so an unavailable Monolith is simply invisible to them.
     *
     * <p>Unavailable means either the village already has an Elder, or it is still too small
     * ({@link ModConfig#MIN_NEARBY_VILLAGERS}). Re-evaluated from scratch every pass, so an Elder
     * dying anywhere - including out of sight, or while this chunk was unloaded - reopens the job.
     */
    private void updateJobSiteAvailability(ServerLevel level) {
        PoiManager poiManager = level.getPoiManager();
        BlockPos pos = getBlockPos();
        if (poiManager.getType(pos).isEmpty()) return; // POI not registered (yet) - nothing to hold

        VillageManager manager = VillageManager.get(level);
        Optional<UUID> resolved = manager.resolveVillage(level, pos);
        boolean hasElder = resolved.isPresent() && manager.getElder(resolved.get()).isPresent();
        boolean available = resolved.isPresent() && !hasElder && villagePopulation(level, resolved.get())
                >= ModConfig.MIN_NEARBY_VILLAGERS.get();

        if (available) {
            if (holdsOwnJobSite) {
                // Guarded: releasing a ticket that is already free logs an error, which is reachable
                // if the POI data was rebuilt out from under a saved flag.
                if (!hasFreeTicket(poiManager, pos)) poiManager.release(pos);
                holdsOwnJobSite = false;
                setChanged();
            }
            return;
        }
        if (holdsOwnJobSite) return;

        if (hasElder && !hasFreeTicket(poiManager, pos)) {
            // The Elder inherited this ticket when it was promoted. Adopt it, so that the Elder
            // dying - even out of sight, or while this chunk was unloaded - still frees the job.
            holdsOwnJobSite = true;
            setChanged();
        } else if (poiManager.take(
                holder -> holder.value() == ModPoiTypes.MONOLITH_POI.get(),
                (holder, candidate) -> candidate.equals(pos),
                pos, 1).isPresent()) {
            holdsOwnJobSite = true;
            setChanged();
        }
        // Otherwise the ticket belongs to a Villager already walking over - leave it alone and
        // retry next pass, rather than yanking a job site out from under them mid-route.
    }

    private static boolean hasFreeTicket(PoiManager poiManager, BlockPos pos) {
        return poiManager.getInRange(holder -> holder.value() == ModPoiTypes.MONOLITH_POI.get(),
                        pos, 1, PoiManager.Occupancy.HAS_SPACE)
                .anyMatch(record -> record.getPos().equals(pos));
    }

    private static int villagePopulation(ServerLevel level, UUID villageId) {
        VillageRegion region = VillageManager.get(level).resolveVillageRegion(level, villageId);
        AABB box = new AABB(region.anchor()).inflate(region.radius());
        return level.getEntitiesOfClass(Villager.class, box).size();
    }

    /**
     * Vanilla has done the work by this point - an unemployed Villager found the Monolith, walked to
     * it and took the Elder profession off it. All that's left is to swap it for the real Elder.
     */
    private void promoteArrivedVillager(ServerLevel level) {
        List<Villager> claimants = level.getEntitiesOfClass(Villager.class,
                new AABB(getBlockPos()).inflate(PROMOTION_SEARCH_RADIUS),
                v -> v.isAlive() && v.getVillagerData().getProfession() == ModVillagers.ELDER.get());
        if (claimants.isEmpty()) return;

        // The promoted Elder inherits the claimant's ticket, so the job site stays claimed with no
        // further work here; updateJobSiteAvailability adopts that ticket on its next pass. Anyone
        // else who turned up (a second Monolith in the same village) is sent back to unemployment.
        Optional<UUID> resolved = VillageManager.get(level).resolveVillage(level, getBlockPos());
        for (Villager claimant : claimants) {
            if (resolved.isEmpty() || !ElderPromotion.promote(level, claimant, resolved.get())) {
                ElderPromotion.rejectClaimant(level, claimant);
            }
        }
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
            upgradeLevels.clear();
            markers.clear();
            poiPoints.clear();
            setChanged();
            return;
        }

        villageId = resolved.get();
        VillageRegion region = manager.resolveVillageRegion(level, villageId);
        BlockPos self = this.getBlockPos();
        for (VillageUpgrade upgrade : VillageUpgrade.values()) {
            upgradeLevels.put(upgrade, upgrade.levelIn(manager, villageId));
        }

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

    public int getUpgradeLevel(VillageUpgrade upgrade) {
        return upgradeLevels.getOrDefault(upgrade, 0);
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
        tag.putBoolean("HoldsOwnJobSite", holdsOwnJobSite);
        tag.putInt("IronGolemCount", ironGolemCount);
        tag.putInt("ActiveWarriorCount", activeWarriorCount);
        tag.putInt("TotalPopulation", totalPopulation);
        // Keyed by name rather than ordinal so reordering the enum can't silently shuffle levels.
        CompoundTag upgradeTag = new CompoundTag();
        upgradeLevels.forEach((upgrade, level) -> upgradeTag.putInt(upgrade.name(), level));
        tag.put("UpgradeLevels", upgradeTag);

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
        holdsOwnJobSite = tag.getBoolean("HoldsOwnJobSite");
        ironGolemCount = tag.getInt("IronGolemCount");
        activeWarriorCount = tag.getInt("ActiveWarriorCount");
        totalPopulation = tag.getInt("TotalPopulation");

        upgradeLevels.clear();
        CompoundTag upgradeTag = tag.getCompound("UpgradeLevels");
        for (VillageUpgrade upgrade : VillageUpgrade.values()) {
            upgradeLevels.put(upgrade, upgradeTag.getInt(upgrade.name()));
        }

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
