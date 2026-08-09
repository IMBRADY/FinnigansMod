package net.finnigan.tommemod.event;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.capability.reputation.ModReputationCapabilities;
import net.finnigan.tommemod.capability.reputation.ReputationTier;
import net.finnigan.tommemod.config.ModConfig;
import net.finnigan.tommemod.entity.ModEntityTypes;
import net.finnigan.tommemod.entity.custom.WarriorVillagerEntity;
import net.finnigan.tommemod.item.custom.HalberdItem;
import net.finnigan.tommemod.village.VillageManager;
import net.finnigan.tommemod.village.VillageRegion;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;
import java.util.UUID;

/**
 * Conscription: sneak + right-click an unemployed Villager while holding a Halberd to convert it
 * into a Warrior Villager, armed with that exact Halberd (consumed from hand) and no armor.
 * Mirrors how the "Guard Villagers" mod converts villagers, rather than spawning from a job-site
 * block - releases the old villager's POI claims and discards it, spawning the Warrior in its place.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class WarriorVillagerSpawnEvents {

    @SubscribeEvent
    public static void onVillagerInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.isShiftKeyDown()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getTarget() instanceof Villager villager) || villager.isBaby()) return;

        ItemStack heldStack = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (!(heldStack.getItem() instanceof HalberdItem)) return;

        VillagerProfession profession = villager.getVillagerData().getProfession();
        if (profession != VillagerProfession.NONE && profession != VillagerProfession.NITWIT) {
            Component message = Component.literal("Only an unemployed villager can be armed")
                    .withStyle(ChatFormatting.GRAY);
            player.displayClientMessage(message, true);
            event.setCanceled(true);
            return;
        }

        VillageManager manager = VillageManager.get(level);
        Optional<UUID> villageId = manager.resolveVillage(level, villager.blockPosition());
        if (villageId.isPresent()) {
            ReputationTier tier = player.getCapability(ModReputationCapabilities.REPUTATION_HANDLER)
                    .map(handler -> handler.getTier(villageId.get()))
                    .orElse(ReputationTier.NOVICE);
            int maxWarriors = ModConfig.maxWarriorsForTier(tier);

            VillageRegion region = manager.resolveVillageRegion(level, villageId.get());
            AABB box = new AABB(region.anchor()).inflate(region.radius());
            // Filter to isAlive() defensively - a just-killed Warrior lingers in the world for its
            // ~1s death animation before actually being removed, and shouldn't still count against the cap.
            long warriorCount = level.getEntitiesOfClass(WarriorVillagerEntity.class, box).stream()
                    .filter(WarriorVillagerEntity::isAlive)
                    .count();
            if (warriorCount >= maxWarriors) {
                Component message = Component.literal("This village already has the max number of warriors based on your village ranking")
                        .withStyle(ChatFormatting.GRAY);
                player.displayClientMessage(message, true);
                event.setCanceled(true);
                return;
            }
        }

        WarriorVillagerEntity warrior = ModEntityTypes.WARRIOR_VILLAGER.get().create(level);
        if (warrior == null) return;

        warrior.copyPosition(villager);
        warrior.setVillagerType(villager.getVillagerData().getType());
        villageId.ifPresent(warrior::setVillageId);

        ItemStack armedHalberd = heldStack.copy();
        armedHalberd.setCount(1);
        warrior.setItemSlot(EquipmentSlot.MAINHAND, armedHalberd);
        if (!player.getAbilities().instabuild) heldStack.shrink(1);

        villager.releasePoi(MemoryModuleType.HOME);
        villager.releasePoi(MemoryModuleType.JOB_SITE);
        villager.releasePoi(MemoryModuleType.MEETING_POINT);
        villager.discard();

        level.addFreshEntity(warrior);
        event.setCanceled(true);
    }
}
