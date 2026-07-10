package net.finnigan.tommemod.item.custom;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ModifierKeyTracker {
    private static final Map<UUID, Boolean> HELD = new ConcurrentHashMap<>();

    public static void set(UUID playerId, boolean held) {
        HELD.put(playerId, held);
    }

    public static boolean isHeld(UUID playerId) {
        return HELD.getOrDefault(playerId, false);
    }
}