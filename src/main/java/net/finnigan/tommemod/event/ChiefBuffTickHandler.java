package net.finnigan.tommemod.event;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.config.ModConfig;
import net.finnigan.tommemod.entity.custom.ElderVillagerEntity;
import net.finnigan.tommemod.village.VillageManager;
import net.finnigan.tommemod.village.VillageRegion;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

/**
 * Applies/refreshes village-population-scaled buffs to the Village Chief while they're physically
 * inside their village's region, and removes them once they leave (or stop being chief).
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class ChiefBuffTickHandler {

    private static final UUID BUFF_MODIFIER_ID = UUID.fromString("a3f5f8c2-3b8e-4e9b-9a2b-3f6a1c0d9e7f");
    private static final Map<UUID, UUID> buffedByVillage = new HashMap<>();
    private static final Map<UUID, Integer> tickCounters = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        int interval = Math.max(1, ModConfig.CHIEF_BUFF_TICK_INTERVAL_TICKS.get());
        int counter = tickCounters.merge(player.getUUID(), 1, Integer::sum);
        if (counter < interval) return;
        tickCounters.put(player.getUUID(), 0);

        // 1. Resolve all configured attributes
        List<Attribute> attributes = resolveAttributes();
        List<AttributeInstance> instances = new ArrayList<>();

        // 2. Fetch the player's specific AttributeInstances
        for (Attribute attribute : attributes) {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance != null) {
                instances.add(instance);
            }
        }

        // 3. Try to apply buffs. If the player is no longer eligible, remove the modifiers.
        boolean applied = tryApplyBuff(player, level, instances);
        if (!applied && buffedByVillage.remove(player.getUUID()) != null) {
            for (AttributeInstance instance : instances) {
                instance.removeModifier(BUFF_MODIFIER_ID);
            }
        }
    }

    private static boolean tryApplyBuff(ServerPlayer player, ServerLevel level, List<AttributeInstance> instances) {
        VillageManager manager = VillageManager.get(level);
        Optional<UUID> villageId = manager.resolveVillage(level, player.blockPosition());
        if (villageId.isEmpty()) return false;

        Optional<UUID> elderUUID = manager.getElder(villageId.get());
        if (elderUUID.isEmpty()) return false;
        if (!(level.getEntity(elderUUID.get()) instanceof ElderVillagerEntity elder)) return false;
        if (!player.getUUID().equals(elder.getChiefUUID())) return false;

        VillageRegion region = manager.resolveVillageRegion(level, villageId.get());
        double padded = region.radius() + ModConfig.CHIEF_BUFF_REGION_PADDING_BLOCKS.get();
        if (player.blockPosition().distSqr(region.anchor()) > padded * padded) return false;

        AABB box = new AABB(region.anchor()).inflate(padded);
        int villagerCount = level.getEntitiesOfClass(Villager.class, box).size();
        double amount = Math.min(ModConfig.CHIEF_BUFF_CAP.get(),
                ModConfig.CHIEF_BUFF_AMOUNT_PER_VILLAGER.get() * villagerCount);

        // Loop through all valid instances and update the modifier
        for (AttributeInstance instance : instances) {
            instance.removeModifier(BUFF_MODIFIER_ID);
            if (amount > 0) {
                instance.addTransientModifier(new AttributeModifier(
                        BUFF_MODIFIER_ID, "tommemod_chief_buff", amount, ModConfig.CHIEF_BUFF_OPERATION.get()));
            }
        }

        buffedByVillage.put(player.getUUID(), villageId.get());
        return true;
    }

    private static List<Attribute> resolveAttributes() {
        List<? extends String> attributeIds = ModConfig.CHIEF_BUFF_ATTRIBUTE.get();
        List<Attribute> resolvedAttributes = new ArrayList<>();

        for (String idString : attributeIds) {
            ResourceLocation id = ResourceLocation.tryParse(idString);
            if (id != null) {
                Attribute attribute = ForgeRegistries.ATTRIBUTES.getValue(id);
                if (attribute != null) {
                    resolvedAttributes.add(attribute);
                }
            }
        }

        return resolvedAttributes;
    }
}