package net.finnigan.tommemod.village.buildings;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.config.ModConfig;
import net.finnigan.tommemod.entity.ModEntityTypes;
import net.finnigan.tommemod.entity.custom.WarriorVillagerEntity;
import net.finnigan.tommemod.village.blueprint.Blueprint;
import net.finnigan.tommemod.village.blueprint.Blueprints;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Barracks: where a village's Warriors come from and where they sleep.
 *
 * <p>Warriors are no longer conscripted by hand. A finished Barracks musters one Warrior per bed,
 * each assigned a bunk - the bed and the chest beside it, where the squires leave its kit. A Warrior
 * that dies is replaced at its bunk one Minecraft day later, for as long as the Barracks stands.
 *
 * <p>Sleep is in shifts. Each bunk's sleep window starts at a different time of day, spread evenly
 * across the barracks, and lasts as long as an ordinary villager's night, so at any moment the same
 * share of the garrison is awake and every Warrior gets the same rest.
 *
 * <p>Barracks beds are for Warriors only: the village's other villagers are kept from claiming them as
 * homes by the barracks holding the beds' POI tickets itself.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public final class BarracksService {

    private static final int TICK_INTERVAL = 100;
    private static final long DAY = 24000L;

    private BarracksService() {
    }

    /** Lays out the bunks of a freshly finished Barracks and musters its Warriors. */
    public static void onBuilt(ServerLevel level, VillageBuildings.Building building) {
        Blueprint bp = Blueprints.get(building.blueprintId);
        if (bp == null) return;
        List<BlockPos> chests = new ArrayList<>();
        List<BlockPos[]> beds = new ArrayList<>();
        for (Blueprint.Cell cell : bp.cells(building.rotation)) {
            BlockPos pos = building.origin.offset(cell.pos());
            if (cell.state().getBlock() instanceof ChestBlock) chests.add(pos);
            if (cell.state().getBlock() instanceof BedBlock && cell.state().getValue(BedBlock.PART) == BedPart.FOOT) {
                Direction facing = cell.state().getValue(BedBlock.FACING);
                beds.add(new BlockPos[]{pos.relative(facing), pos});
            }
        }
        int n = beds.size();
        for (int i = 0; i < n; i++) {
            BlockPos head = beds.get(i)[0];
            BlockPos foot = beds.get(i)[1];
            BlockPos chest = null;
            double best = 6.5;
            for (BlockPos c : chests) {
                double d = c.distSqr(foot);
                if (d < best) {
                    best = d;
                    chest = c;
                }
            }
            int shift = (int) (i * DAY / n);
            building.slots.add(VillageBuildings.newSlot(head, foot, chest, shift));
        }
        VillageBuildings.get(level).setDirty();
        holdBeds(level, building);
        for (VillageBuildings.BarracksSlot slot : building.slots) muster(level, building, slot);
    }

    /** Whether a bunk with this shift start is asleep at this time of day. */
    public static boolean isSleepShift(long dayTime, int shiftStart) {
        long into = Math.floorMod(dayTime - shiftStart, DAY);
        return into < ModConfig.WARRIOR_SLEEP_TICKS.get();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        if (server == null || server.getTickCount() % TICK_INTERVAL != 0) return;

        for (ServerLevel level : server.getAllLevels()) {
            VillageBuildings data = VillageBuildings.get(level);
            for (VillageBuildings.Building b : data.all()) {
                if (!BuildingPurpose.BARRACKS.equals(b.purpose) || b.slots.isEmpty()) continue;
                if (!level.isLoaded(b.centre()) || !data.isStanding(level, b)) continue;
                holdBeds(level, b);
                long now = level.getGameTime();
                for (VillageBuildings.BarracksSlot slot : b.slots) {
                    boolean vacant = slot.warrior == null;
                    boolean respawnDue = slot.diedAt >= 0 && now - slot.diedAt >= ModConfig.WARRIOR_RESPAWN_TICKS.get();
                    if (vacant || respawnDue) muster(level, b, slot);
                }
            }
        }
    }

    /** Marks a Barracks Warrior's bunk empty from the moment it dies, starting the day's wait. */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof WarriorVillagerEntity warrior)) return;
        if (!(warrior.level() instanceof ServerLevel level) || warrior.getBarracksId() == null) return;
        VillageBuildings data = VillageBuildings.get(level);
        VillageBuildings.Building b = data.get(warrior.getBarracksId());
        if (b == null) return;
        for (VillageBuildings.BarracksSlot slot : b.slots) {
            if (warrior.getUUID().equals(slot.warrior)) {
                slot.diedAt = level.getGameTime();
                data.setDirty();
            }
        }
    }

    private static void muster(ServerLevel level, VillageBuildings.Building b, VillageBuildings.BarracksSlot slot) {
        if (!level.isLoaded(slot.bedHead) || !(level.getBlockState(slot.bedHead).getBlock() instanceof BedBlock)) return;
        WarriorVillagerEntity warrior = ModEntityTypes.WARRIOR_VILLAGER.get().create(level);
        if (warrior == null) return;
        BlockPos stand = standingSpotBeside(level, slot.bedFoot);
        warrior.moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, level.getRandom().nextFloat() * 360F, 0F);
        warrior.setVillagerType(VillagerType.byBiome(level.getBiome(stand)));
        warrior.setVillageId(b.villageId);
        warrior.assignBunk(b.id, slot.bedHead, slot.chest, slot.shiftStart);
        level.addFreshEntity(warrior);
        slot.warrior = warrior.getUUID();
        slot.diedAt = -1;
        VillageBuildings.get(level).setDirty();
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, warrior.getX(), warrior.getY() + 1, warrior.getZ(), 10, 0.4, 0.6, 0.4, 0);
        level.playSound(null, stand, SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1F, 1F);
    }

    private static BlockPos standingSpotBeside(ServerLevel level, BlockPos foot) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos p = foot.relative(d);
            if (level.getBlockState(p).getCollisionShape(level, p).isEmpty()
                    && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()
                    && !level.getBlockState(p.below()).getCollisionShape(level, p.below()).isEmpty()) {
                return p;
            }
        }
        return foot.above();
    }

    /**
     * Keeps each barracks bed's home ticket in the barracks' own hands, so ordinary villagers never
     * move in. A villager that claimed one while the barracks was still going up is turned out.
     */
    private static void holdBeds(ServerLevel level, VillageBuildings.Building b) {
        PoiManager poi = level.getPoiManager();
        for (VillageBuildings.BarracksSlot slot : b.slots) {
            BlockPos head = slot.bedHead;
            if (poi.getType(head).isEmpty()) continue;
            boolean free = poi.getInRange(h -> h.is(PoiTypes.HOME), head, 0, PoiManager.Occupancy.HAS_SPACE)
                    .anyMatch(r -> r.getPos().equals(head));
            if (!free) {
                Villager squatter = villagerHomedAt(level, head);
                if (squatter == null) continue; // already ours
                squatter.releasePoi(MemoryModuleType.HOME);
            }
            poi.take(h -> h.is(PoiTypes.HOME), (h, p) -> p.equals(head), head, 1);
        }
    }

    @Nullable
    private static Villager villagerHomedAt(ServerLevel level, BlockPos head) {
        GlobalPos target = GlobalPos.of(level.dimension(), head);
        for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(head).inflate(64))) {
            Optional<GlobalPos> home = v.getBrain().getMemory(MemoryModuleType.HOME);
            if (home.isPresent() && home.get().equals(target)) return v;
        }
        return null;
    }

    /** The living Warrior assigned to a bunk, if it is loaded. */
    @Nullable
    public static WarriorVillagerEntity warriorAt(ServerLevel level, VillageBuildings.BarracksSlot slot) {
        if (slot.warrior == null) return null;
        Entity e = level.getEntity(slot.warrior);
        return e instanceof WarriorVillagerEntity w && w.isAlive() ? w : null;
    }
}
