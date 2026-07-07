package net.finnigan.tommemod.event;

import net.finnigan.tommemod.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = "tommemod", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class HarmonyEffectHandler {

    private static final int CHECK_INTERVAL_TICKS = 20;      // scan once per second, not every tick
    private static final int DETECTION_RADIUS_HORIZONTAL = 20; // blocks
    private static final int DETECTION_RADIUS_VERTICAL = 8;    // blocks
    private static final int LINGER_TICKS = 2400;               // 3.33 mins
    private static final int BUFF_REFRESH_DURATION = CHECK_INTERVAL_TICKS + 10; // small buffer over the check interval

    // Runtime-only — resets on server restart, which is fine for a temporary buff timer.
    private static final Map<UUID, Integer> lingerRemaining = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Player player = event.player;
        if (player.level().isClientSide) return;
        if (player.tickCount % CHECK_INTERVAL_TICKS != 0) return;

        boolean holdingHarmony =
                player.getMainHandItem().getItem() == ModItems.HARMONY.get()
                        || player.getOffhandItem().getItem() == ModItems.HARMONY.get();

        UUID id = player.getUUID();

        if (holdingHarmony && isNearPlayingJukebox(player)) {
            lingerRemaining.put(id, LINGER_TICKS);
            applyBuffs(player);
        } else {
            int remaining = lingerRemaining.getOrDefault(id, 0) - CHECK_INTERVAL_TICKS;

            if (remaining > 0) {
                lingerRemaining.put(id, remaining);
                applyBuffs(player); // still lingering — keep buffs topped up
            } else {
                lingerRemaining.remove(id); // fully expired, let effects run out naturally
            }
        }
    }

    private static boolean isNearPlayingJukebox(Player player) {
        BlockPos center = player.blockPosition();

        BlockPos min = center.offset(-DETECTION_RADIUS_HORIZONTAL, -DETECTION_RADIUS_VERTICAL, -DETECTION_RADIUS_HORIZONTAL);
        BlockPos max = center.offset(DETECTION_RADIUS_HORIZONTAL, DETECTION_RADIUS_VERTICAL, DETECTION_RADIUS_HORIZONTAL);

        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (player.level().getBlockEntity(pos) instanceof JukeboxBlockEntity jukebox) {
                if (jukebox.isRecordPlaying()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void applyBuffs(Player player) {
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, BUFF_REFRESH_DURATION, 1, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, BUFF_REFRESH_DURATION, 1, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, BUFF_REFRESH_DURATION, 0, false, true));
    }
}