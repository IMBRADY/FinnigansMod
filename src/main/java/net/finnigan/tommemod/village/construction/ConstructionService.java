package net.finnigan.tommemod.village.construction;

import net.finnigan.tommemod.village.blueprint.Blueprint;
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

        if (refundTo != null) {
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

    static void finish(ServerLevel level, ConstructionSite site) {
        BuilderWorkHandler.releaseSite(level, site.id());
        ConstructionManager.get(level).remove(site.id());

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
