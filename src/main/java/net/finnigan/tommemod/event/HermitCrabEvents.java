package net.finnigan.tommemod.event;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.entity.custom.HermitCrabEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class HermitCrabEvents {

    @SubscribeEvent
    public static void onShiftRightClickGround(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (!player.isShiftKeyDown() || event.getLevel().isClientSide()) return;

        List<HermitCrabEntity> shoulderRiders = event.getLevel().getEntitiesOfClass(HermitCrabEntity.class,
                player.getBoundingBox().inflate(4.0),
                crab -> crab.isRidingShoulder() && crab.isOwnedBy(player));
        if (shoulderRiders.isEmpty()) return;

        BlockPos landPos = event.getPos().relative(event.getFace());
        shoulderRiders.get(0).dismountShoulder(landPos);
    }
}
