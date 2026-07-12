package net.finnigan.tommemod.item.custom.BlossomKatanaHelpers;

import net.finnigan.tommemod.TommeMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class BlossomAuraEvents {

    private static final List<BlossomAuraZone> ACTIVE_ZONES = new ArrayList<>();

    public static void spawnZone(ServerLevel level, double x, double y, double z, double radius, int durationTicks) {
        ACTIVE_ZONES.add(new BlossomAuraZone(level, x, y, z, radius, durationTicks));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ACTIVE_ZONES.removeIf(BlossomAuraZone::tick);
    }
}