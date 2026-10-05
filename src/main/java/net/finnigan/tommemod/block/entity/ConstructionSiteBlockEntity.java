package net.finnigan.tommemod.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Legacy: a construction site from before blueprint mode, when placing a Construction Banner built a
 * structure on its own, one block a second. New buildings are planned at a Blueprint Stand and built
 * by Builder Villagers (see village.construction); this only exists so a banner already standing in a
 * world finishes - or, if broken, reverts - the build it started. Nothing creates new ones.
 */
public class ConstructionSiteBlockEntity extends BlockEntity {

    private static final int TICKS_PER_BLOCK = 20;

    private record QueueEntry(BlockPos pos, BlockState target, BlockState original) {
    }

    private boolean started = false;
    private final Deque<QueueEntry> remaining = new ArrayDeque<>();
    private final List<QueueEntry> placed = new ArrayList<>();
    private int placeCooldown = 1;
    private boolean completed = false;
    private boolean demolished = false;

    public ConstructionSiteBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CONSTRUCTION_SITE.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ConstructionSiteBlockEntity be) {
        if (level.isClientSide()) return;
        if (!be.started || be.completed) return;

        if (be.remaining.isEmpty()) {
            be.completed = true;
            level.removeBlock(pos, false);
            return;
        }

        if (be.placeCooldown-- > 0) return;
        be.placeCooldown = TICKS_PER_BLOCK;

        QueueEntry next = be.remaining.poll();
        level.setBlock(next.pos(), next.target(), 3);
        be.placed.add(next);
        be.setChanged();
    }

    /** The banner was removed before the build finished - revert the partial structure. */
    public void demolish(Level level) {
        if (demolished || completed || !started) return;
        demolished = true;

        for (QueueEntry entry : placed) {
            level.setBlock(entry.pos(), entry.original(), 3);
        }
        remaining.clear();
        placed.clear();
    }

    // ---- Persistence ----
    // Every BlockState a legacy site handled was a plain default state, so each is persisted as its
    // registry id and reconstructed via defaultBlockState() on load.

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (started) tag.putString("BuildingType", "LEGACY");
        tag.putInt("PlaceCooldown", placeCooldown);
        tag.putBoolean("Completed", completed);
        tag.putBoolean("Demolished", demolished);
        tag.put("Remaining", writeQueue(remaining));
        tag.put("Placed", writeQueue(placed));
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        started = tag.contains("BuildingType");
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
