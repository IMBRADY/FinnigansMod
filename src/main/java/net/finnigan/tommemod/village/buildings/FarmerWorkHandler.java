package net.finnigan.tommemod.village.buildings;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.block.ModBlocks;
import net.finnigan.tommemod.config.ModConfig;
import net.finnigan.tommemod.village.VillageManager;
import net.finnigan.tommemod.village.blueprint.Blueprint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Farmers that actually farm: through the working day each Farmer goes from ripe crop to ripe crop
 * on every field it looks after - the farmland round its composter, and every Farm Plot in its
 * village - harvesting each one and replanting it on the spot, and sweeping up any crops lying on
 * the ground as it goes. Vanilla Farmers only work whatever happens to be under their feet and
 * leave most of the harvest where it falls; this is what keeps a whole field turning over.
 *
 * <p>While the village has a Bank, the harvest is carried rather than eaten: the Farmer keeps a small
 * reserve of each crop in its own inventory (what vanilla villagers live on, share and breed with)
 * and seed enough to replant, and banks the rest. When the workday ends it walks the haul to the
 * Bank's vault and pays it in. Without a Bank, everything goes into the Farmer's own inventory as
 * before, and what doesn't fit is dropped for the village to pick up.
 *
 * <p>Driven from outside the villager's brain, by setting its walk target, so it sits alongside the
 * vanilla farmer behaviours instead of replacing them.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public final class FarmerWorkHandler {

    private static final int INTERVAL = 5;
    private static final int RESCAN_TICKS = 200;
    private static final int GIVE_UP_TICKS = 240;
    private static final int DELIVERY_PATIENCE_TICKS = 600;
    private static final double REACH_SQR = 2.0 * 2.0;
    private static final double VAULT_REACH_SQR = 3.0 * 3.0;
    private static final double PLOT_RANGE = 64;
    private static final int VERTICAL_RANGE = 4;
    /** How much of each crop a Farmer keeps on itself while the bank takes the rest. */
    private static final int RESERVE = 16;
    private static final String STASH_TAG = "TommeFarmHarvest";
    private static final List<Item> SEED_PRIORITY = List.of(Items.WHEAT_SEEDS, Items.POTATO, Items.CARROT, Items.BEETROOT_SEEDS);

    private static final class FarmerState {
        List<BlockPos> farmland = List.of();
        long scannedAt = Long.MIN_VALUE;
        @Nullable
        BlockPos target;
        int targetTicks;
        final Map<BlockPos, Long> skipUntil = new HashMap<>();
        int deliveryTicks;
    }

    private static final Map<UUID, FarmerState> STATES = new HashMap<>();
    /** Which Farmer is on its way to which crop, so two never walk to the same one. */
    private static final Map<BlockPos, UUID> CLAIMS = new HashMap<>();

    private FarmerWorkHandler() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        if (server == null || server.getTickCount() % INTERVAL != 0) return;

        Set<UUID> seen = new java.util.HashSet<>();
        for (ServerLevel level : server.getAllLevels()) {
            for (Villager v : level.getEntities(EntityTypeTest.forClass(Villager.class), FarmerWorkHandler::isFarmer)) {
                seen.add(v.getUUID());
                tick(level, v, STATES.computeIfAbsent(v.getUUID(), k -> new FarmerState()));
            }
        }
        // Farmers that died, changed job or unloaded.
        STATES.keySet().retainAll(seen);
        CLAIMS.values().retainAll(seen);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        STATES.clear();
        CLAIMS.clear();
    }

    private static boolean isFarmer(Villager v) {
        return v.isAlive() && v.getVillagerData().getProfession() == VillagerProfession.FARMER;
    }

    private static void tick(ServerLevel level, Villager v, FarmerState state) {
        var brain = v.getBrain();
        if (brain.isActive(Activity.PANIC) || brain.isActive(Activity.RAID) || brain.isActive(Activity.HIDE)
                || brain.isActive(Activity.PRE_RAID)) {
            dropTarget(v, state);
            return;
        }

        if (brain.isActive(Activity.WORK)) {
            state.deliveryTicks = 0;
            work(level, v, state);
        } else {
            dropTarget(v, state);
            if (!stash(v).isEmpty()) deliver(level, v, state);
        }
    }

    // ---- Working the fields ----

    private static void work(ServerLevel level, Villager v, FarmerState state) {
        long now = level.getGameTime();
        sweepUpDrops(level, v);

        if (now - state.scannedAt >= RESCAN_TICKS) {
            state.farmland = scanFarmland(level, v);
            state.scannedAt = now;
            state.skipUntil.values().removeIf(until -> until <= now);
        }

        if (state.target != null) {
            BlockPos crop = state.target;
            if (!isWorkable(level, v, crop)) {
                dropTarget(v, state);
            } else if (v.distanceToSqr(crop.getX() + 0.5, crop.getY(), crop.getZ() + 0.5) <= REACH_SQR) {
                workCrop(level, v, crop);
                dropTarget(v, state);
            } else if ((state.targetTicks += INTERVAL) > GIVE_UP_TICKS) {
                // Can't get there - try the rest of the field and come back to this one later.
                state.skipUntil.put(crop, now + RESCAN_TICKS * 3L);
                dropTarget(v, state);
            } else {
                walkTo(v, crop);
                return;
            }
        }

        BlockPos next = nearestWorkable(level, v, state, now);
        if (next == null) return;
        state.target = next;
        state.targetTicks = 0;
        CLAIMS.put(next, v.getUUID());
        walkTo(v, next);
    }

    private static void dropTarget(Villager v, FarmerState state) {
        if (state.target != null) CLAIMS.remove(state.target, v.getUUID());
        state.target = null;
        state.targetTicks = 0;
    }

    private static void walkTo(Villager v, BlockPos pos) {
        v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(pos, 0.5F, 1));
        v.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new net.minecraft.world.entity.ai.behavior.BlockPosTracker(pos));
    }

    @Nullable
    private static BlockPos nearestWorkable(ServerLevel level, Villager v, FarmerState state, long now) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos farmland : state.farmland) {
            BlockPos crop = farmland.above();
            UUID claimant = CLAIMS.get(crop);
            if (claimant != null && !claimant.equals(v.getUUID())) continue;
            Long skip = state.skipUntil.get(crop);
            if (skip != null && skip > now) continue;
            double d = v.distanceToSqr(crop.getX() + 0.5, crop.getY(), crop.getZ() + 0.5);
            // Ripe crops first; bare farmland only when there is nothing to harvest nearer than it.
            if (isRipe(level.getBlockState(crop))) d *= 0.25;
            if (d < bestDist && isWorkable(level, v, crop)) {
                bestDist = d;
                best = crop;
            }
        }
        return best;
    }

    private static boolean isRipe(BlockState state) {
        return state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state);
    }

    /** A ripe crop to harvest, or bare farmland the Farmer has something to plant in. */
    private static boolean isWorkable(ServerLevel level, Villager v, BlockPos crop) {
        if (!level.isLoaded(crop) || !level.getBlockState(crop.below()).is(Blocks.FARMLAND)) return false;
        BlockState state = level.getBlockState(crop);
        if (isRipe(state)) return true;
        return state.isAir() && seedToPlant(v) != null;
    }

    private static void workCrop(ServerLevel level, Villager v, BlockPos crop) {
        BlockState state = level.getBlockState(crop);
        List<ItemStack> drops = new ArrayList<>();
        Item seed;
        if (isRipe(state)) {
            drops.addAll(Block.getDrops(state, level, crop, null, v, ItemStack.EMPTY));
            level.destroyBlock(crop, false, v);
            seed = state.getBlock().getCloneItemStack(level, crop, state).getItem();
            // Replant from the harvest itself, so a field never goes bare for want of seed.
            if (!takeFrom(drops, seed) && !takeSeed(v, seed)) seed = null;
        } else {
            seed = seedToPlant(v);
            if (seed != null && !takeSeed(v, seed)) seed = null;
        }

        if (seed instanceof BlockItem blockItem) {
            BlockState planted = blockItem.getBlock().defaultBlockState();
            level.setBlockAndUpdate(crop, planted);
            level.gameEvent(GameEvent.BLOCK_PLACE, crop, GameEvent.Context.of(v, planted));
            level.playSound(null, crop, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        for (ItemStack stack : drops) keep(level, v, stack);
    }

    /** Picks up any crop or seed lying within reach - vanilla Farmers walk past most of their harvest. */
    private static void sweepUpDrops(ServerLevel level, Villager v) {
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, v.getBoundingBox().inflate(2.0, 1.0, 2.0),
                e -> e.isAlive() && !e.hasPickUpDelay() && isFarmProduce(e.getItem()))) {
            ItemStack stack = item.getItem().copy();
            item.discard();
            keep(level, v, stack);
        }
    }

    private static boolean isFarmProduce(ItemStack stack) {
        return stack.is(Items.WHEAT) || stack.is(Items.WHEAT_SEEDS) || stack.is(Items.POTATO) || stack.is(Items.POISONOUS_POTATO)
                || stack.is(Items.CARROT) || stack.is(Items.BEETROOT) || stack.is(Items.BEETROOT_SEEDS);
    }

    @Nullable
    private static Item seedToPlant(Villager v) {
        CompoundTag stash = stash(v);
        for (Item seed : SEED_PRIORITY) {
            if (v.getInventory().countItem(seed) > 0 || stash.getLong(key(seed)) > 0) return seed;
        }
        return null;
    }

    private static boolean takeFrom(List<ItemStack> drops, Item item) {
        for (ItemStack stack : drops) {
            if (stack.is(item) && !stack.isEmpty()) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }

    private static boolean takeSeed(Villager v, Item seed) {
        SimpleContainer inv = v.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(seed)) {
                stack.shrink(1);
                inv.setChanged();
                return true;
            }
        }
        CompoundTag stash = stash(v);
        long banked = stash.getLong(key(seed));
        if (banked <= 0) return false;
        putStash(v, seed, banked - 1);
        return true;
    }

    /**
     * Where a harvested stack goes: the Farmer's reserve first, then - if there's a Bank to take it
     * to - its haul for the vault, and failing both, onto the ground.
     */
    private static void keep(ServerLevel level, Villager v, ItemStack stack) {
        if (stack.isEmpty()) return;
        SimpleContainer inv = v.getInventory();
        int room = Math.max(0, RESERVE * (isSeed(stack.getItem()) ? 2 : 1) - inv.countItem(stack.getItem()));
        if (room > 0) {
            ItemStack reserve = stack.split(Math.min(room, stack.getCount()));
            ItemStack left = inv.addItem(reserve);
            stack.grow(left.getCount());
        }
        if (stack.isEmpty()) return;

        UUID village = VillageManager.get(level).resolveVillage(level, v.blockPosition()).orElse(null);
        if (village != null && VillageBuildings.get(level).hasBank(level, village)) {
            CompoundTag stash = stash(v);
            putStash(v, stack.getItem(), stash.getLong(key(stack.getItem())) + stack.getCount());
            return;
        }
        ItemStack left = inv.addItem(stack);
        if (!left.isEmpty()) v.spawnAtLocation(left);
    }

    private static boolean isSeed(Item item) {
        return item == Items.WHEAT_SEEDS || item == Items.BEETROOT_SEEDS;
    }

    private static List<BlockPos> scanFarmland(ServerLevel level, Villager v) {
        List<BlockPos> out = new ArrayList<>();
        java.util.Set<BlockPos> seen = new java.util.HashSet<>();
        BlockPos centre = v.getBrain().getMemory(MemoryModuleType.JOB_SITE)
                .filter(site -> site.dimension() == level.dimension())
                .map(GlobalPos::pos)
                .orElse(v.blockPosition());
        int r = ModConfig.FARMER_WORK_RADIUS_BLOCKS.get();
        for (BlockPos p : BlockPos.betweenClosed(centre.offset(-r, -VERTICAL_RANGE, -r), centre.offset(r, VERTICAL_RANGE, r))) {
            if (level.getBlockState(p).is(Blocks.FARMLAND) && seen.add(p.immutable())) out.add(p.immutable());
        }

        // Every Farm Plot nearby, wherever it is in the village.
        VillageBuildings data = VillageBuildings.get(level);
        for (VillageBuildings.Building plot : data.all()) {
            if (!BuildingPurpose.FARM.equals(plot.purpose) || plot.centre().distSqr(v.blockPosition()) > PLOT_RANGE * PLOT_RANGE) continue;
            if (!data.isStanding(level, plot)) continue;
            Blueprint bp = plot.blueprint();
            if (bp == null) continue;
            for (Blueprint.Cell c : bp.cells(plot.rotation)) {
                BlockPos p = plot.origin.offset(c.pos());
                if (c.state().is(Blocks.FARMLAND) && level.getBlockState(p).is(Blocks.FARMLAND) && seen.add(p)) out.add(p);
            }
        }
        return out;
    }

    // ---- End of the day: the haul to the bank ----

    private static void deliver(ServerLevel level, Villager v, FarmerState state) {
        UUID village = VillageManager.get(level).resolveVillage(level, v.blockPosition()).orElse(null);
        BlockPos vault = village != null ? vaultOf(level, village) : null;
        if (vault == null) {
            // No bank to take it to any more - it goes back to being the Farmer's own.
            unstash(v);
            return;
        }
        boolean there = v.distanceToSqr(vault.getX() + 0.5, vault.getY(), vault.getZ() + 0.5) <= VAULT_REACH_SQR;
        // A Farmer that can't find its way (or has gone to bed) still gets its haul paid in.
        if (there || v.isSleeping() || (state.deliveryTicks += INTERVAL) > DELIVERY_PATIENCE_TICKS) {
            payIn(level, v, village, vault);
            state.deliveryTicks = 0;
            return;
        }
        walkTo(v, vault);
    }

    @Nullable
    private static BlockPos vaultOf(ServerLevel level, UUID village) {
        for (VillageBuildings.Building bank : VillageBuildings.get(level).standing(level, village, BuildingPurpose.BANK)) {
            Blueprint bp = bank.blueprint();
            if (bp == null) continue;
            for (Blueprint.Cell c : bp.cells(bank.rotation)) {
                if (!c.state().is(ModBlocks.VILLAGE_VAULT.get())) continue;
                BlockPos pos = bank.origin.offset(c.pos());
                if (level.getBlockState(pos).is(ModBlocks.VILLAGE_VAULT.get())) return pos;
            }
        }
        return null;
    }

    private static void payIn(ServerLevel level, Villager v, UUID village, BlockPos vault) {
        CompoundTag stash = stash(v);
        VillageManager manager = VillageManager.get(level);
        for (String id : stash.getAllKeys()) {
            Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(id));
            if (item != null && item != Items.AIR) manager.bankDeposit(village, item, stash.getLong(id));
        }
        v.getPersistentData().remove(STASH_TAG);
        level.playSound(null, vault, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.6F, 1.4F);
        level.playSound(null, v.blockPosition(), SoundEvents.VILLAGER_WORK_FARMER, SoundSource.NEUTRAL, 1.0F, 1.0F);
    }

    private static void unstash(Villager v) {
        CompoundTag stash = stash(v);
        v.getPersistentData().remove(STASH_TAG);
        for (String id : stash.getAllKeys()) {
            Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(id));
            if (item == null || item == Items.AIR) continue;
            long count = stash.getLong(id);
            while (count > 0) {
                int n = (int) Math.min(count, item.getMaxStackSize());
                ItemStack left = v.getInventory().addItem(new ItemStack(item, n));
                if (!left.isEmpty()) v.spawnAtLocation(left);
                count -= n;
            }
        }
    }

    // The haul is kept on the villager's own saved data, so it survives a reload mid-day.

    private static CompoundTag stash(Villager v) {
        return v.getPersistentData().getCompound(STASH_TAG);
    }

    private static void putStash(Villager v, Item item, long count) {
        CompoundTag stash = stash(v);
        if (count > 0) stash.putLong(key(item), count);
        else stash.remove(key(item));
        if (stash.isEmpty()) v.getPersistentData().remove(STASH_TAG);
        else v.getPersistentData().put(STASH_TAG, stash);
    }

    private static String key(Item item) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        return id != null ? id.toString() : "minecraft:air";
    }
}
