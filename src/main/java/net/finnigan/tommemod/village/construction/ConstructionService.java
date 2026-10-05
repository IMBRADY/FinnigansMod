package net.finnigan.tommemod.village.construction;

import net.finnigan.tommemod.village.VillageFunds;
import net.finnigan.tommemod.village.VillageManager;
import net.finnigan.tommemod.village.blueprint.Blueprint;
import net.finnigan.tommemod.village.buildings.BarracksService;
import net.finnigan.tommemod.village.buildings.BuildingPurpose;
import net.finnigan.tommemod.village.buildings.VillageBuildings;
import net.finnigan.tommemod.village.buildings.WallPerimeter;
import net.finnigan.tommemod.villager.ModVillagers;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.finnigan.tommemod.village.blueprint.BlueprintModeManager;
import net.finnigan.tommemod.village.blueprint.BlueprintPlanner;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Starting, cancelling and finishing construction sites - everything that changes which sites exist. */
public final class ConstructionService {

    private ConstructionService() {
    }

    public static ConstructionSite start(ServerLevel level, UUID villageId, @Nullable UUID owner,
                                         BlueprintPlanner.Plan plan, List<Blueprint.Cost> paid) {
        ConstructionSite site = new ConstructionSite(UUID.randomUUID(), villageId, owner, plan.blueprint().id(),
                plan.rotation(), plan.origin(), plan.bounds(), level.getGameTime(), List.copyOf(plan.placements()), paid);
        ConstructionManager.get(level).add(site);

        BoundingBox b = plan.bounds();
        level.playSound(null, BlockPos.containing(b.getCenter().getX(), b.minY(), b.getCenter().getZ()),
                SoundEvents.VILLAGER_WORK_MASON, SoundSource.NEUTRAL, 1.0F, 1.0F);
        BlueprintModeManager.syncSites(level);
        return site;
    }

    /**
     * Stops a site, puts back every block builders changed (newest first, so a door comes out before
     * the wall it is in) and refunds what was paid. Reverting deliberately skips neighbour updates and
     * drops: half a door being removed must not spit out a whole door.
     */
    public static void cancel(ServerLevel level, ConstructionSite site, @Nullable ServerPlayer refundTo) {
        BuilderWorkHandler.releaseSite(level, site.id());
        ConstructionManager.get(level).remove(site.id());

        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
        for (Map.Entry<BlockPos, BlockState> e : site.originalsNewestFirst()) {
            level.setBlock(e.getKey(), e.getValue(), flags);
        }

        if (refundTo != null && VillageFunds.bankActive(refundTo, site.villageId())) {
            // A village with a bank paid from it (first), so the refund goes back into it.
            for (Blueprint.Cost c : site.paid()) VillageManager.get(level).bankDeposit(site.villageId(), c.item(), c.count());
            refundTo.displayClientMessage(Component.literal("Cancelled the " + site.displayName() + " - materials returned to the bank")
                    .withStyle(ChatFormatting.YELLOW), true);
        } else if (refundTo != null) {
            for (Blueprint.Cost c : site.paid()) {
                int remaining = c.count();
                while (remaining > 0) {
                    int n = Math.min(remaining, c.item().getMaxStackSize());
                    ItemStack stack = new ItemStack(c.item(), n);
                    if (!refundTo.getInventory().add(stack)) refundTo.drop(stack, false);
                    remaining -= n;
                }
            }
            refundTo.displayClientMessage(Component.literal("Cancelled the " + site.displayName() + " - materials refunded")
                    .withStyle(ChatFormatting.YELLOW), true);
        }
        BlueprintModeManager.syncSites(level);
    }

    /**
     * Gets every Builder off and out of a finished building. They teleport about while working and
     * can end the job on the roof, or shut inside a room with no way out they can path through, so
     * each one in or on it is set down on open ground just outside its footprint.
     */
    private static void evacuate(ServerLevel level, BoundingBox bounds) {
        AABB area = new AABB(bounds.minX() - 1, bounds.minY() - 1, bounds.minZ() - 1,
                bounds.maxX() + 2, bounds.maxY() + 4, bounds.maxZ() + 2);
        List<Villager> builders = level.getEntitiesOfClass(Villager.class, area,
                v -> v.isAlive() && v.getVillagerData().getProfession() == ModVillagers.BUILDER.get());
        for (Villager v : builders) {
            BlockPos spot = safeSpotOutside(level, bounds, v.blockPosition());
            if (spot == null) continue;
            level.sendParticles(ParticleTypes.POOF, v.getX(), v.getY() + 0.8, v.getZ(), 6, 0.2, 0.4, 0.2, 0.01);
            v.getNavigation().stop();
            v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            v.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
            v.resetFallDistance();
        }
    }

    /** The nearest open, solid-floored spot in a ring just outside the footprint, at ground level. */
    @Nullable
    private static BlockPos safeSpotOutside(ServerLevel level, BoundingBox bounds, BlockPos near) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int r = 2; r <= 8 && best == null; r++) {
            for (int x = bounds.minX() - r; x <= bounds.maxX() + r; x++) {
                for (int z = bounds.minZ() - r; z <= bounds.maxZ() + r; z++) {
                    boolean onRing = x == bounds.minX() - r || x == bounds.maxX() + r || z == bounds.minZ() - r || z == bounds.maxZ() + r;
                    if (!onRing) continue;
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    BlockPos feet = new BlockPos(x, y, z);
                    if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()) continue;
                    if (!level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) continue;
                    if (!level.getFluidState(feet).isEmpty() || !level.getFluidState(feet.below()).isEmpty()) continue;
                    if (!level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP)) continue;
                    double d = feet.distSqr(near);
                    if (d < bestDist) {
                        bestDist = d;
                        best = feet;
                    }
                }
            }
        }
        return best;
    }

    static void finish(ServerLevel level, ConstructionSite site) {
        BuilderWorkHandler.releaseSite(level, site.id());
        ConstructionManager.get(level).remove(site.id());
        evacuate(level, site.bounds());

        Blueprint bp = site.blueprint();
        if (bp != null) {
            VillageBuildings.Building building = VillageBuildings.get(level).record(site.villageId(), bp, site.rotation(), site.origin(), site.bounds());
            // Kept so the Chief can demolish it later for a refund, with the ground put back as it was.
            building.paid = List.copyOf(site.paid());
            List<Map.Entry<BlockPos, BlockState>> originals = site.originalsNewestFirst();
            for (int i = originals.size() - 1; i >= 0; i--) building.originals.put(originals.get(i).getKey(), originals.get(i).getValue());
            if (BuildingPurpose.BARRACKS.equals(bp.purpose())) BarracksService.onBuilt(level, building);
            if (BuildingPurpose.WALL.equals(bp.purpose())) WallPerimeter.recompute(level, site.villageId());
        }

        BoundingBox b = site.bounds();
        double cx = (b.minX() + b.maxX() + 1) / 2.0;
        double cz = (b.minZ() + b.maxZ() + 1) / 2.0;
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, cx, b.maxY() + 1.0, cz, 40,
                b.getXSpan() / 2.0, 1.0, b.getZSpan() / 2.0, 0.0);
        level.playSound(null, BlockPos.containing(cx, b.minY() + 1, cz), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1.2F, 1.0F);

        ServerPlayer owner = site.owner() != null ? level.getServer().getPlayerList().getPlayer(site.owner()) : null;
        if (owner != null) {
            owner.sendSystemMessage(Component.literal("✔ Your builders have finished the " + site.displayName() + "!")
                    .withStyle(ChatFormatting.GREEN));
            owner.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.2F);
        }
        BlueprintModeManager.syncSites(level);
    }
}
