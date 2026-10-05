package net.finnigan.tommemod.village.construction;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.config.ModConfig;
import net.finnigan.tommemod.village.VillageRegion;
import net.finnigan.tommemod.village.blueprint.BlueprintPlanner;
import net.finnigan.tommemod.village.blueprint.BlueprintPlanner.Placement;
import net.finnigan.tommemod.villager.ModVillagers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Builder Villagers actually building.
 *
 * <p>Every second, each active site calls in the Builders within range, sharing them out so no site
 * is starved while another has a crowd. Every tick, each assigned Builder takes the next block from
 * its site, walks to it, and places it. They do not have to play fair: a Builder that cannot reach
 * its block in a couple of seconds - it is up on a roof, or across a ravine - simply appears next to
 * it, and one with nowhere to stand places the block from where it is.
 *
 * <p>Builders are vanilla Villagers with a profession, not a custom entity, so there are no goals to
 * add; their brain is steered through its walk and look targets instead, and they work through the
 * night (a sleeping Builder with a site to finish is woken up).
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public final class BuilderWorkHandler {

    private static final int REASSIGN_INTERVAL = 20;
    private static final double REACH_SQR = 4.5 * 4.5;
    /** Ticks a Builder gets to walk to its block before it gives up walking and teleports. */
    private static final int WALK_PATIENCE = 40;
    private static final double TELEPORT_IMMEDIATELY_SQR = 28 * 28;
    private static final float WALK_SPEED = 0.7F;

    private static final class Worker {
        final UUID villager;
        UUID site;
        @Nullable
        Integer job;
        int cooldown;
        int travel;

        Worker(UUID villager, UUID site) {
            this.villager = villager;
            this.site = site;
        }
    }

    private static final Map<ServerLevel, Map<UUID, Worker>> WORKERS = new WeakHashMap<>();

    private BuilderWorkHandler() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        if (server == null) return;

        for (ServerLevel level : server.getAllLevels()) {
            ConstructionManager manager = ConstructionManager.get(level);
            Map<UUID, Worker> workers = WORKERS.computeIfAbsent(level, l -> new HashMap<>());
            if (manager.isEmpty()) {
                if (!workers.isEmpty()) dismissAll(level, workers);
                continue;
            }

            if (level.getGameTime() % REASSIGN_INTERVAL == 0) reassign(level, manager, workers);

            boolean progressed = false;
            for (Iterator<Worker> it = workers.values().iterator(); it.hasNext(); ) {
                Worker w = it.next();
                ConstructionSite site = manager.get(w.site);
                Entity e = level.getEntity(w.villager);
                if (site == null || !(e instanceof Villager villager) || !villager.isAlive()) {
                    if (site != null && w.job != null) site.release(w.job);
                    if (e instanceof Villager v) clearHand(v);
                    it.remove();
                    continue;
                }
                progressed |= tickWorker(level, site, w, villager);
            }

            List<ConstructionSite> finished = new ArrayList<>();
            for (ConstructionSite site : manager.all()) {
                if (site.isFinished()) finished.add(site);
            }
            for (ConstructionSite site : finished) {
                ConstructionService.finish(level, site);
                progressed = true;
            }
            if (progressed) manager.setDirty();
        }
    }

    /** Builder Villagers within {@code radius} of a point - the ones a site there could call on. */
    public static List<Villager> buildersNear(ServerLevel level, BlockPos centre, double radius) {
        AABB box = new AABB(centre).inflate(radius, 48, radius);
        return level.getEntitiesOfClass(Villager.class, box, BuilderWorkHandler::isBuilder);
    }

    public static int countBuilders(ServerLevel level, VillageRegion region) {
        return buildersNear(level, region.anchor(), region.radius() + 16).size();
    }

    private static boolean isBuilder(Villager v) {
        return v.isAlive() && !v.isBaby() && v.getVillagerData().getProfession() == ModVillagers.BUILDER.get();
    }

    /** How many Builders are currently on a given site. */
    public static int workersOn(ServerLevel level, UUID siteId) {
        Map<UUID, Worker> workers = WORKERS.get(level);
        if (workers == null) return 0;
        int n = 0;
        for (Worker w : workers.values()) if (w.site.equals(siteId)) n++;
        return n;
    }

    /** Drops every Builder's claim on a site that is about to stop existing. */
    static void releaseSite(ServerLevel level, UUID siteId) {
        Map<UUID, Worker> workers = WORKERS.get(level);
        if (workers == null) return;
        for (Iterator<Worker> it = workers.values().iterator(); it.hasNext(); ) {
            Worker w = it.next();
            if (!w.site.equals(siteId)) continue;
            if (level.getEntity(w.villager) instanceof Villager v) clearHand(v);
            it.remove();
        }
    }

    // ---- Assignment ----

    private static void reassign(ServerLevel level, ConstructionManager manager, Map<UUID, Worker> workers) {
        double radius = ModConfig.BUILDER_SEARCH_RADIUS_BLOCKS.get();
        List<ConstructionSite> sites = new ArrayList<>(manager.all());
        Map<UUID, List<ConstructionSite>> candidates = new HashMap<>();
        for (ConstructionSite site : sites) {
            for (Villager v : buildersNear(level, site.bounds().getCenter(), radius)) {
                candidates.computeIfAbsent(v.getUUID(), k -> new ArrayList<>()).add(site);
            }
        }

        // Share Builders out: each goes to whichever of its reachable sites has the fewest so far,
        // the older site winning a tie. Sorted by UUID so the answer is stable from one pass to the next.
        Map<UUID, Integer> load = new HashMap<>();
        List<UUID> builders = new ArrayList<>(candidates.keySet());
        builders.sort(Comparator.naturalOrder());
        Map<UUID, UUID> assignment = new HashMap<>();
        for (UUID builder : builders) {
            ConstructionSite best = null;
            for (ConstructionSite s : candidates.get(builder)) {
                if (best == null || load.getOrDefault(s.id(), 0) < load.getOrDefault(best.id(), 0)) best = s;
            }
            if (best == null) continue;
            assignment.put(builder, best.id());
            load.merge(best.id(), 1, Integer::sum);
        }

        for (Iterator<Map.Entry<UUID, Worker>> it = workers.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Worker> e = it.next();
            Worker w = e.getValue();
            UUID newSite = assignment.get(e.getKey());
            if (newSite != null && newSite.equals(w.site)) continue;
            ConstructionSite old = manager.get(w.site);
            if (old != null && w.job != null) old.release(w.job);
            w.job = null;
            if (newSite == null) {
                if (level.getEntity(w.villager) instanceof Villager v) clearHand(v);
                it.remove();
            } else {
                w.site = newSite;
            }
        }
        for (Map.Entry<UUID, UUID> e : assignment.entrySet()) {
            workers.computeIfAbsent(e.getKey(), k -> new Worker(k, e.getValue()));
        }
    }

    private static void dismissAll(ServerLevel level, Map<UUID, Worker> workers) {
        for (Worker w : workers.values()) {
            if (level.getEntity(w.villager) instanceof Villager v) clearHand(v);
        }
        workers.clear();
    }

    // ---- Working ----

    /** Returns true if a block was placed. */
    private static boolean tickWorker(ServerLevel level, ConstructionSite site, Worker w, Villager v) {
        if (v.isSleeping()) v.stopSleeping();

        if (w.job == null) {
            if (w.cooldown > 0) {
                w.cooldown--;
                return false;
            }
            w.job = site.claimNext(level);
            if (w.job == null) {
                // Nothing left to hand out, but others are still finishing - wait by the site.
                clearHand(v);
                if (v.distanceToSqr(Vec3.atCenterOf(centreOf(site.bounds()))) > 12 * 12) {
                    walkTo(v, centreOf(site.bounds()));
                }
                return false;
            }
            w.travel = 0;
            hold(v, site.placement(w.job));
        }

        Placement job = site.placement(w.job);
        Vec3 target = Vec3.atCenterOf(job.pos());
        v.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(job.pos()));
        v.getLookControl().setLookAt(target);

        double distSqr = v.getEyePosition().distanceToSqr(target);
        if (distSqr > REACH_SQR) {
            w.travel++;
            if (w.travel == 1 || w.travel % 20 == 0) walkTo(v, job.pos());
            if (w.travel < WALK_PATIENCE && distSqr < TELEPORT_IMMEDIATELY_SQR) return false;

            BlockPos spot = findStandingSpot(level, site, job.pos());
            if (spot != null) teleport(level, v, spot);
            // No footing anywhere near (a spire, a block out over water): it is placed from here.
        }

        place(level, site, w.job, v);
        site.complete(w.job);
        int ticks = ModConfig.BUILDER_TICKS_PER_BLOCK.get();
        w.cooldown = job.phase() == BlueprintPlanner.PHASE_CLEAR ? Math.max(0, ticks / 2 - 1) : ticks - 1;
        w.job = null;
        return true;
    }

    private static void place(ServerLevel level, ConstructionSite site, int index, Villager v) {
        Placement job = site.placement(index);
        BlockPos pos = job.pos();
        BlockState current = level.getBlockState(pos);
        BlockState target = job.state();
        if (current.equals(target)) return;

        site.recordOriginal(pos, current);

        if (target.isAir()) {
            // The break effect, without drops: cleared terrain is not a free resource.
            level.levelEvent(2001, pos, Block.getId(current));
            level.setBlock(pos, target, Block.UPDATE_ALL);
            return;
        }

        BlockPos partnerPos = partnerOf(target, pos);
        Integer partnerIdx = partnerPos != null ? site.indexAt(partnerPos) : null;
        if (partnerIdx != null) {
            // Doors and beds are two blocks that delete each other when either is alone, so both
            // halves go down together. The partner's own queue entry then finds itself done.
            BlockState partnerState = site.placement(partnerIdx).state();
            site.recordOriginal(partnerPos, level.getBlockState(partnerPos));
            pushEntitiesOut(level, pos, target);
            pushEntitiesOut(level, partnerPos, partnerState);
            level.setBlock(pos, target, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            level.setBlock(partnerPos, partnerState, Block.UPDATE_ALL);
        } else {
            BlockState shaped = Block.updateFromNeighbourShapes(target, level, pos);
            // A torch or lantern whose support is not up yet would shape itself into air; place it
            // as designed instead - its support arriving later is exactly the update it waits for.
            if (shaped.isAir()) shaped = target;
            pushEntitiesOut(level, pos, shaped);
            level.setBlock(pos, shaped, Block.UPDATE_ALL);
        }

        var sound = target.getSoundType(level, pos, v);
        level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.5F, sound.getPitch() * 0.8F);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, target),
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.05);
    }

    @Nullable
    private static BlockPos partnerOf(BlockState state, BlockPos pos) {
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            return state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
        }
        if (state.getBlock() instanceof BedBlock && state.hasProperty(BedBlock.PART)) {
            Direction facing = state.getValue(BedBlock.FACING);
            return state.getValue(BedBlock.PART) == BedPart.FOOT ? pos.relative(facing) : pos.relative(facing.getOpposite());
        }
        return null;
    }

    /** Nothing gets sealed inside a new wall: anyone standing in the block is lifted out on top of it. */
    private static void pushEntitiesOut(ServerLevel level, BlockPos pos, BlockState state) {
        var shape = state.getCollisionShape(level, pos);
        if (shape.isEmpty()) return;
        AABB solid = shape.bounds().move(pos);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, solid, en -> !en.isSpectator())) {
            e.teleportTo(e.getX(), solid.maxY, e.getZ());
            // A lift or teleport must not count as a fall - landing would trample freshly laid farmland.
            e.resetFallDistance();
        }
    }

    // ---- Moving ----

    private static void walkTo(Villager v, BlockPos pos) {
        v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(pos, WALK_SPEED, 2));
    }

    private static void teleport(ServerLevel level, Villager v, BlockPos spot) {
        level.sendParticles(ParticleTypes.POOF, v.getX(), v.getY() + 0.8, v.getZ(), 6, 0.2, 0.4, 0.2, 0.01);
        v.getNavigation().stop();
        v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        v.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
        v.resetFallDistance();
        level.sendParticles(ParticleTypes.POOF, v.getX(), v.getY() + 0.8, v.getZ(), 6, 0.2, 0.4, 0.2, 0.01);
    }

    /**
     * Somewhere a Builder can stand within reach of {@code target}: two free blocks on something
     * solid, not inside the work. Nearest first, preferring footing at or just below the block.
     */
    @Nullable
    private static BlockPos findStandingSpot(ServerLevel level, ConstructionSite site, BlockPos target) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        BlockPos.MutableBlockPos feet = new BlockPos.MutableBlockPos();
        for (int dy = 1; dy >= -3; dy--) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    feet.set(target.getX() + dx, target.getY() + dy, target.getZ() + dz);
                    if (feet.equals(target) || feet.above().equals(target)) continue;
                    double d = feet.distToCenterSqr(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
                    if (d >= bestDist || d > 16) continue;
                    if (!canStand(level, site, feet)) continue;
                    best = feet.immutable();
                    bestDist = d;
                }
            }
        }
        return best;
    }

    private static boolean canStand(ServerLevel level, ConstructionSite site, BlockPos feet) {
        BlockPos head = feet.above();
        BlockPos below = feet.below();
        if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()) return false;
        if (!level.getBlockState(head).getCollisionShape(level, head).isEmpty()) return false;
        if (!level.getFluidState(feet).isEmpty()) return false;
        if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) return false;
        return !site.solidPendingAt(level, feet) && !site.solidPendingAt(level, head);
    }

    // ---- Holding ----

    /** Builders carry what they are about to place in their crossed arms (a shovel when digging). */
    private static void hold(Villager v, Placement job) {
        Item item = job.state().isAir() ? Items.IRON_SHOVEL : job.state().getBlock().asItem();
        if (item == Items.AIR) item = Items.STICK;
        v.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(item));
    }

    private static void clearHand(Villager v) {
        if (!v.getMainHandItem().isEmpty()) v.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
    }

    /** Builders are careful where they tread: they never trample farmland, even dropping off a fence. */
    @SubscribeEvent
    public static void onTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (event.getEntity() instanceof Villager v && isBuilder(v)) event.setCanceled(true);
    }

    private static BlockPos centreOf(BoundingBox box) {
        return new BlockPos(box.getCenter().getX(), box.minY(), box.getCenter().getZ());
    }
}
