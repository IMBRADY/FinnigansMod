package net.finnigan.tommemod.entity.custom;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Mod.EventBusSubscriber
public class NoteSpawnScheduler {

    private static final List<PendingSpawn> PENDING = new ArrayList<>();

    public static void schedule(Level level, Player player, Vec3 lookDir, int delayTicks) {
        PENDING.add(new PendingSpawn(level, player, lookDir, delayTicks));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Iterator<PendingSpawn> it = PENDING.iterator();
        while (it.hasNext()) {
            PendingSpawn pending = it.next();
            if (pending.tick()) {
                pending.spawn();
                it.remove();
            }
        }
    }

    private static class PendingSpawn {
        final Level level;
        final Player player;
        final Vec3 lookDir;
        int ticksRemaining;

        PendingSpawn(Level level, Player player, Vec3 lookDir, int ticksRemaining) {
            this.level = level;
            this.player = player;
            this.lookDir = lookDir;
            this.ticksRemaining = ticksRemaining;
        }

        // Returns true when it's time to spawn
        boolean tick() {
            return ticksRemaining-- <= 0;
        }

        void spawn() {
            if (!player.isAlive()) return;

            MusicNoteEntity note = new MusicNoteEntity(level, player);

            double spread = 1.0;
            Vec3 eyePos = player.getEyePosition();
            Vec3 spawnPos = eyePos.add(
                    (level.random.nextDouble() - 0.5) * 2 * spread,
                    (level.random.nextDouble() - 0.5) * 2 * spread,
                    (level.random.nextDouble() - 0.5) * 2 * spread
            );

            Vec3 velocity = lookDir.normalize().scale(0.8);

            note.setPos(spawnPos);
            note.setDeltaMovement(velocity);
            note.setInitialLookDir(lookDir);

            level.addFreshEntity(note);
        }
    }
}