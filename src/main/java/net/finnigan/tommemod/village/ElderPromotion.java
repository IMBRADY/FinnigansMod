package net.finnigan.tommemod.village;

import net.finnigan.tommemod.entity.ModEntityTypes;
import net.finnigan.tommemod.entity.custom.ElderVillagerEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

import java.util.UUID;

/**
 * Turning a Villager that has just claimed a Monolith into that village's Elder.
 *
 * <p>Split out from the block entity because the swap has one non-obvious rule: the Villager's
 * JOB_SITE POI ticket is deliberately <em>not</em> released on the way out. Holding it is what keeps
 * the Monolith reading as a claimed job block for as long as the Elder lives, so no second Villager
 * ever paths towards it. MonolithBlockEntity owns the other end of that - it frees the ticket again
 * once the village has no Elder.
 */
public final class ElderPromotion {

    private ElderPromotion() {
    }

    /**
     * Replaces {@code villager} with a freshly spawned Elder for {@code villageId}. Returns false
     * without touching the Villager if the village already has an Elder or the spawn failed, which
     * is the case a second Monolith in the same village runs into.
     */
    public static boolean promote(ServerLevel level, Villager villager, UUID villageId) {
        VillageManager manager = VillageManager.get(level);
        if (manager.getElder(villageId).isPresent()) return false;

        ElderVillagerEntity elder = ModEntityTypes.ELDER_VILLAGER.get().create(level);
        if (elder == null) return false;

        elder.copyPosition(villager);
        elder.setVillageId(villageId);
        if (!level.addFreshEntity(elder)) return false;

        if (!manager.tryRegisterElder(villageId, elder.getUUID())) {
            elder.discard();
            return false;
        }

        BlockPos promotionPos = villager.blockPosition();
        villager.releasePoi(MemoryModuleType.HOME);
        villager.releasePoi(MemoryModuleType.MEETING_POINT);
        // JOB_SITE is left claimed on purpose - see the class javadoc.
        villager.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);
        villager.discard();

        strikeCeremonialLightning(level, promotionPos);
        return true;
    }

    /**
     * Sends a Villager that reached a Monolith it can't have back to being unemployed, freeing the
     * job site it claimed on the way. Used when the village already has an Elder.
     */
    public static void rejectClaimant(ServerLevel level, Villager villager) {
        villager.releasePoi(MemoryModuleType.JOB_SITE);
        villager.releasePoi(MemoryModuleType.POTENTIAL_JOB_SITE);
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.NONE));
        villager.refreshBrain(level);
    }

    private static void strikeCeremonialLightning(ServerLevel level, BlockPos pos) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) return;

        bolt.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        bolt.setVisualOnly(true); // sound + visual strike only - no fire, no damage, no charged creepers
        level.addFreshEntity(bolt);
    }
}
