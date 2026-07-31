package net.finnigan.tommemod.block.entity;

import net.finnigan.tommemod.config.ModConfig;
import net.finnigan.tommemod.village.BuildingStructures;
import net.finnigan.tommemod.village.BuildingType;
import net.finnigan.tommemod.village.ConstructionSiteRegistry;
import net.finnigan.tommemod.village.PathBuilder;
import net.finnigan.tommemod.village.VillageManager;
import net.finnigan.tommemod.village.VillageRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A construction site started by placing a Construction Banner. Structures are built two blocks
 * diagonally away from the banner itself (see STRUCTURE_OFFSET) so the queued placements never
 * collide with the banner block that's tracking them. One queued block is placed every
 * ModConfig.BUILDER_HUB_TICK_INTERVAL_TICKS ticks; each queue entry remembers the block it's
 * replacing so an interrupted build can be demolished back to the original terrain.
 */
public class ConstructionSiteBlockEntity extends BlockEntity {

    private static final BlockPos STRUCTURE_OFFSET = new BlockPos(2, 0, 2);

    private record QueueEntry(BlockPos pos, BlockState target, BlockState original) {
    }

    @Nullable
    private BuildingType buildingType;
    @Nullable
    private UUID villageId;
    @Nullable
    private BlockPos doorFrontPos;
    private Direction facing = Direction.SOUTH;
    private final Deque<QueueEntry> remaining = new ArrayDeque<>();
    private final List<QueueEntry> placed = new ArrayList<>();
    private int placeCooldown = 1;
    private boolean completed = false;
    private boolean demolished = false;

    public ConstructionSiteBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CONSTRUCTION_SITE.get(), pos, state);
    }

    public boolean isCompleted() {
        return completed;
    }

    /** World-space ground point just outside this building's door (never touching the door itself),
     * for path-building to connect to once the structure finishes - null for building types with no
     * door concept (e.g. wall segments), or before initialize() has run. */
    @Nullable
    public BlockPos getDoorFrontPos() {
        return doorFrontPos;
    }

    /**
     * Validates the site (village proximity, footprint height variance), auto-flattens the
     * footprint if it's uneven but within tolerance, and queues the structure's blocks. Returns
     * false (leaving this BE uninitialized) if the spot is invalid - the caller is responsible for
     * removing the banner block and refunding the player in that case.
     */
    public boolean initialize(ServerLevel level, BuildingType type, UUID villageId, Direction facing) {
        VillageManager manager = VillageManager.get(level);
        if (!manager.isEstablished(villageId)) return false;

        VillageRegion region = manager.resolveVillageRegion(level, villageId);
        double padded = region.radius() + ModConfig.BUILDER_HUB_REGION_PADDING_BLOCKS.get();
        if (this.getBlockPos().distSqr(region.anchor()) > padded * padded) return false;

        List<Map.Entry<BlockPos, BlockState>> structure = BuildingStructures.forType(type, facing);
        if (structure.isEmpty()) return false;

        BlockPos structureOrigin = this.getBlockPos().offset(STRUCTURE_OFFSET);

        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (Map.Entry<BlockPos, BlockState> e : structure) {
            minX = Math.min(minX, e.getKey().getX());
            maxX = Math.max(maxX, e.getKey().getX());
            minZ = Math.min(minZ, e.getKey().getZ());
            maxZ = Math.max(maxZ, e.getKey().getZ());
        }

        int minHeight = Integer.MAX_VALUE, maxHeight = Integer.MIN_VALUE;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int h = level.getHeight(Heightmap.Types.WORLD_SURFACE, structureOrigin.getX() + x, structureOrigin.getZ() + z) - 1;
                minHeight = Math.min(minHeight, h);
                maxHeight = Math.max(maxHeight, h);
            }
        }

        if (maxHeight - minHeight > ModConfig.BUILDER_HUB_MAX_FOOTPRINT_VARIANCE.get()) return false;

        // Auto-flatten: fill up to minHeight where the ground is lower, clear anything above it.
        // This happens immediately (not queued), so it isn't reverted by a later demolish - only
        // the structure blocks placed after this point are.
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int worldX = structureOrigin.getX() + x;
                int worldZ = structureOrigin.getZ() + z;
                int currentSurface = level.getHeight(Heightmap.Types.WORLD_SURFACE, worldX, worldZ) - 1;
                for (int y = currentSurface + 1; y <= minHeight; y++) {
                    level.setBlock(new BlockPos(worldX, y, worldZ), Blocks.DIRT.defaultBlockState(), 3);
                }
                for (int y = currentSurface; y > minHeight; y--) {
                    level.setBlock(new BlockPos(worldX, y, worldZ), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }

        int baseY = minHeight + 1;
        this.buildingType = type;
        this.villageId = villageId;
        this.facing = facing;
        this.remaining.clear();
        for (Map.Entry<BlockPos, BlockState> e : structure) {
            BlockPos rel = e.getKey();
            BlockPos abs = new BlockPos(structureOrigin.getX() + rel.getX(), baseY + rel.getY(), structureOrigin.getZ() + rel.getZ());
            this.remaining.add(new QueueEntry(abs, e.getValue(), level.getBlockState(abs)));
        }
        this.placed.clear();
        this.placeCooldown = Math.max(1, ModConfig.BUILDER_HUB_TICK_INTERVAL_TICKS.get());

        BlockPos doorOffset = BuildingStructures.doorOffset(type, facing);
        this.doorFrontPos = doorOffset != null
                ? new BlockPos(structureOrigin.getX() + doorOffset.getX(), baseY + doorOffset.getY(), structureOrigin.getZ() + doorOffset.getZ())
                : null;

        ConstructionSiteRegistry.register(level, this.getBlockPos());
        setChanged();
        return true;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ConstructionSiteBlockEntity be) {
        if (level.isClientSide()) return;
        if (be.buildingType == null || be.completed) return;

        if (be.remaining.isEmpty()) {
            be.completed = true;
            if (level instanceof ServerLevel serverLevel) {
                ConstructionSiteRegistry.unregister(serverLevel, pos);
                if (be.doorFrontPos != null) {
                    PathBuilder.buildPathToDoor(serverLevel, be.doorFrontPos);
                }
            }
            level.removeBlock(pos, false);
            return;
        }

        if (be.placeCooldown-- > 0) return;
        be.placeCooldown = Math.max(1, ModConfig.BUILDER_HUB_TICK_INTERVAL_TICKS.get());

        QueueEntry next = be.remaining.poll();
        level.setBlock(next.pos(), next.target(), 3);
        be.placed.add(next);
        be.setChanged();
    }

    /** Player broke the banner before completion: full refund to their inventory, plus reverting
     * every block placed so far back to what was originally there. */
    public void refundAndDemolish(Level level, Player player) {
        if (demolished || completed || buildingType == null) return;
        demolished = true;
        if (level instanceof ServerLevel serverLevel) {
            ConstructionSiteRegistry.unregister(serverLevel, this.getBlockPos());
        }

        for (QueueEntry entry : placed) {
            level.setBlock(entry.pos(), entry.original(), 3);
        }
        remaining.clear();
        placed.clear();

        giveOrDrop(player, new ItemStack(Items.EMERALD, buildingType.getEmeraldCost()));
        giveOrDrop(player, new ItemStack(buildingType.getResourceItem(), buildingType.getResourceCount()));
    }

    /** Non-player removal (explosion, fire, etc.) - revert the partial structure. No refund since
     * there's no player to give it to. */
    public void demolishOnly(Level level) {
        if (demolished || completed || buildingType == null) return;
        demolished = true;
        if (level instanceof ServerLevel serverLevel) {
            ConstructionSiteRegistry.unregister(serverLevel, this.getBlockPos());
        }

        for (QueueEntry entry : placed) {
            level.setBlock(entry.pos(), entry.original(), 3);
        }
        remaining.clear();
        placed.clear();
    }

    private static void giveOrDrop(Player player, ItemStack stack) {
        if (stack.isEmpty()) return;
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    /** ConstructionSiteRegistry is in-memory only (not persisted), so an active site that survives a
     * chunk unload/reload or server restart needs to re-add itself here once it's actually attached
     * to a level again - load(CompoundTag) alone doesn't guarantee that yet. Safe to call
     * redundantly alongside initialize()'s own registration, since the registry is a de-duping Set. */
    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide() && level instanceof ServerLevel serverLevel
                && buildingType != null && !completed && !demolished) {
            ConstructionSiteRegistry.register(serverLevel, this.getBlockPos());
        }
    }

    // ---- Persistence ----
    // Every BlockState this BE ever handles comes from BuildingStructures/Blocks.DIRT/Blocks.AIR,
    // none of which use non-default block properties, so it's enough to persist each state's
    // registry id and reconstruct via defaultBlockState() on load.

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (buildingType != null) tag.putString("BuildingType", buildingType.name());
        if (villageId != null) tag.putUUID("VillageId", villageId);
        tag.putString("Facing", facing.getName());
        if (doorFrontPos != null) {
            CompoundTag doorTag = new CompoundTag();
            doorTag.putInt("X", doorFrontPos.getX());
            doorTag.putInt("Y", doorFrontPos.getY());
            doorTag.putInt("Z", doorFrontPos.getZ());
            tag.put("DoorFrontPos", doorTag);
        }
        tag.putInt("PlaceCooldown", placeCooldown);
        tag.putBoolean("Completed", completed);
        tag.putBoolean("Demolished", demolished);
        tag.put("Remaining", writeQueue(remaining));
        tag.put("Placed", writeQueue(placed));
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        buildingType = tag.contains("BuildingType") ? BuildingType.valueOf(tag.getString("BuildingType")) : null;
        villageId = tag.hasUUID("VillageId") ? tag.getUUID("VillageId") : null;
        Direction loadedFacing = tag.contains("Facing") ? Direction.byName(tag.getString("Facing")) : null;
        facing = loadedFacing != null ? loadedFacing : Direction.SOUTH;
        if (tag.contains("DoorFrontPos")) {
            CompoundTag doorTag = tag.getCompound("DoorFrontPos");
            doorFrontPos = new BlockPos(doorTag.getInt("X"), doorTag.getInt("Y"), doorTag.getInt("Z"));
        } else {
            doorFrontPos = null;
        }
        placeCooldown = tag.getInt("PlaceCooldown");
        completed = tag.getBoolean("Completed");
        demolished = tag.getBoolean("Demolished");

        remaining.clear();
        remaining.addAll(readQueue(tag.getList("Remaining", Tag.TAG_COMPOUND)));
        placed.clear();
        placed.addAll(readQueue(tag.getList("Placed", Tag.TAG_COMPOUND)));
    }

    private static ListTag writeQueue(Iterable<QueueEntry> entries) {
        ListTag list = new ListTag();
        for (QueueEntry entry : entries) {
            CompoundTag e = new CompoundTag();
            e.putInt("X", entry.pos().getX());
            e.putInt("Y", entry.pos().getY());
            e.putInt("Z", entry.pos().getZ());
            e.putString("Target", blockId(entry.target()));
            e.putString("Original", blockId(entry.original()));
            list.add(e);
        }
        return list;
    }

    private static List<QueueEntry> readQueue(ListTag list) {
        List<QueueEntry> entries = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            BlockPos pos = new BlockPos(e.getInt("X"), e.getInt("Y"), e.getInt("Z"));
            entries.add(new QueueEntry(pos, blockState(e.getString("Target")), blockState(e.getString("Original"))));
        }
        return entries;
    }

    private static String blockId(BlockState state) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        return id != null ? id.toString() : "minecraft:air";
    }

    private static BlockState blockState(String id) {
        ResourceLocation loc = ResourceLocation.tryParse(id);
        var block = loc != null ? ForgeRegistries.BLOCKS.getValue(loc) : null;
        return block != null ? block.defaultBlockState() : Blocks.AIR.defaultBlockState();
    }
}
