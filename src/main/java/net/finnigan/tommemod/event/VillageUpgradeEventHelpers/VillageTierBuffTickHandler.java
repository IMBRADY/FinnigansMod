package net.finnigan.tommemod.event.VillageUpgradeEventHelpers;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.capability.reputation.ModReputationCapabilities;
import net.finnigan.tommemod.capability.reputation.ReputationTier;
import net.finnigan.tommemod.config.ModConfig;
import net.finnigan.tommemod.village.VillageManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The tier 2+ half of the Village Tier upgrade: a village that has grown that far looks after its own
 * while they stand in it.
 *
 * <p>Deliberately not folded into {@link net.finnigan.tommemod.event.ChiefBuffTickHandler}, and given
 * a modifier id of its own so the two stack: the Chief buff is a reward for one player's office and
 * scales with population, this one is a property of the village that every player who belongs to it
 * gets. A Chief in a tier 3 village should feel both.
 *
 * <p>"Their village" is read as reputation: a player the village knows (Apprentice or better) is one
 * of its own. A stranger standing inside the walls is not, and gets nothing.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class VillageTierBuffTickHandler {

    private static final UUID BUFF_MODIFIER_ID = UUID.fromString("6c1d0f24-8a37-4b5e-9d18-7e2a4c93b501");
    private static final ReputationTier MIN_STANDING = ReputationTier.APPRENTICE;

    /** Which players currently carry the buff, so it can be taken back off when they walk out. */
    private static final Map<UUID, UUID> buffedByVillage = new HashMap<>();
    private static final Map<UUID, Integer> tickCounters = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        // Shares the Chief buff's cadence: the two answer the same question about the same player at
        // the same boundary, and running them out of step makes one flicker against the other.
        int interval = Math.max(1, ModConfig.CHIEF_BUFF_TICK_INTERVAL_TICKS.get());
        int counter = tickCounters.merge(player.getUUID(), 1, Integer::sum);
        if (counter < interval) return;
        tickCounters.put(player.getUUID(), 0);

        List<AttributeInstance> instances = resolveInstances(player);
        if (instances.isEmpty()) return;

        if (!tryApplyBuff(player, level, instances) && buffedByVillage.remove(player.getUUID()) != null) {
            for (AttributeInstance instance : instances) {
                instance.removeModifier(BUFF_MODIFIER_ID);
            }
        }
    }

    private static boolean tryApplyBuff(ServerPlayer player, ServerLevel level, List<AttributeInstance> instances) {
        VillageManager manager = VillageManager.get(level);
        Optional<UUID> villageId = manager.resolveVillage(level, player.blockPosition());
        if (villageId.isEmpty()) return false;

        if (manager.getVillageTier(villageId.get()) < ModConfig.VILLAGE_TIER_BUFF_MIN_TIER.get()) return false;

        ReputationTier standing = player.getCapability(ModReputationCapabilities.REPUTATION_HANDLER)
                .map(handler -> handler.getTier(villageId.get()))
                .orElse(ReputationTier.NOVICE);
        if (!standing.isAtLeast(MIN_STANDING)) return false;

        double amount = ModConfig.VILLAGE_TIER_BUFF_AMOUNT.get();
        for (AttributeInstance instance : instances) {
            instance.removeModifier(BUFF_MODIFIER_ID);
            if (amount > 0) {
                instance.addTransientModifier(new AttributeModifier(BUFF_MODIFIER_ID,
                        "tommemod_village_tier_buff", amount, AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
        }

        buffedByVillage.put(player.getUUID(), villageId.get());
        return true;
    }

    private static List<AttributeInstance> resolveInstances(ServerPlayer player) {
        List<AttributeInstance> instances = new ArrayList<>();
        for (String idString : ModConfig.VILLAGE_TIER_BUFF_ATTRIBUTES.get()) {
            ResourceLocation id = ResourceLocation.tryParse(idString);
            if (id == null) continue;
            Attribute attribute = ForgeRegistries.ATTRIBUTES.getValue(id);
            if (attribute == null) continue;
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance != null) instances.add(instance);
        }
        return instances;
    }
}
