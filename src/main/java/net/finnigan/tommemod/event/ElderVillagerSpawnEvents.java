package net.finnigan.tommemod.event;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.config.ModConfig;
import net.finnigan.tommemod.entity.ModEntityTypes;
import net.finnigan.tommemod.entity.custom.ElderVillagerEntity;
import net.finnigan.tommemod.village.VillageManager;
import net.finnigan.tommemod.village.VillageRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;
import java.util.UUID;

/**
 * Placing an Enchanting Table near an established village (population permitting) summons that
 * village's sole Elder Villager. Enforces one Elder per village via VillageManager's registry.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class ElderVillagerSpawnEvents {

    @SubscribeEvent
    public static void onEntityPlace(BlockEvent.EntityPlaceEvent event) {
        if (!event.getPlacedBlock().is(Blocks.ENCHANTING_TABLE)) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        BlockPos pos = event.getPos();
        VillageManager manager = VillageManager.get(level);

        Optional<UUID> villageIdOpt = manager.resolveVillage(level, pos);
        if (villageIdOpt.isEmpty()) return;

        UUID villageId = villageIdOpt.get();
        if (manager.getElder(villageId).isPresent()) return;

        VillageRegion region = manager.resolveVillageRegion(level, villageId);
        AABB box = new AABB(region.anchor()).inflate(region.radius());
        int villagerCount = level.getEntitiesOfClass(Villager.class, box).size();
        if (villagerCount < ModConfig.MIN_NEARBY_VILLAGERS.get()) return;

        ElderVillagerEntity elder = ModEntityTypes.ELDER_VILLAGER.get().create(level);
        if (elder == null) return;

        elder.setVillageId(villageId);
        elder.moveTo(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 0.0F, 0.0F);
        if (!level.addFreshEntity(elder)) return;

        manager.tryRegisterElder(villageId, elder.getUUID());
    }
}
