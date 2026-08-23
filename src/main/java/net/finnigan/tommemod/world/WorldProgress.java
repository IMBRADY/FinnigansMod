package net.finnigan.tommemod.world;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * World-wide progression flags - things that have happened once, to this save, and stay happened.
 *
 * <p>Deliberately stored on the <em>Overworld's</em> data storage rather than the level that set the
 * flag. {@link SavedData} is per-dimension: the ender dragon dies in the End, but the mobs that care
 * about it spawn in the Overworld, so a flag written to the End's storage would be invisible where it
 * is read. Every accessor here routes through {@link MinecraftServer#overworld()} so there is exactly
 * one copy of the truth no matter who asks.
 */
public class WorldProgress extends SavedData {

    private static final String DATA_NAME = "tommemod_world_progress";
    private static final String KEY_DRAGON_DEFEATED = "DragonDefeated";

    private boolean dragonDefeated;

    public static WorldProgress get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(WorldProgress::load, WorldProgress::new, DATA_NAME);
    }

    public static WorldProgress get(ServerLevel level) {
        return get(level.getServer());
    }

    /**
     * Safe to call from anywhere, including the client, where it always answers {@code false} - none of
     * this is synced, so any client-side behaviour must not depend on it.
     */
    public static boolean isDragonDefeated(Level level) {
        if (level.isClientSide()) return false;

        MinecraftServer server = level.getServer();
        return server != null && get(server).dragonDefeated;
    }

    public boolean isDragonDefeated() {
        return this.dragonDefeated;
    }

    /** @return true if this call is what flipped the flag, false if it was already set. */
    public boolean markDragonDefeated() {
        if (this.dragonDefeated) return false;

        this.dragonDefeated = true;
        this.setDirty();
        return true;
    }

    private static WorldProgress load(CompoundTag tag) {
        WorldProgress progress = new WorldProgress();
        progress.dragonDefeated = tag.getBoolean(KEY_DRAGON_DEFEATED);
        return progress;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean(KEY_DRAGON_DEFEATED, this.dragonDefeated);
        return tag;
    }
}
