package net.finnigan.tommemod.client;

/**
 * Client-side mirror of the local player's reputation standing in their nearest village, kept
 * fresh by ReputationHudSyncHandler via SyncReputationHudPacket. Read by the inventory-screen
 * reputation badge mixins - simple static holder since there's only ever one local player's HUD.
 */
public class ClientReputationHud {

    private static boolean hasVillage = false;
    private static int tierOrdinal = 0;
    private static int score = 0;
    private static int nextThreshold = -1;
    private static boolean chief = false;

    public static void update(boolean hasVillage, int tierOrdinal, int score, int nextThreshold, boolean chief) {
        ClientReputationHud.hasVillage = hasVillage;
        ClientReputationHud.tierOrdinal = tierOrdinal;
        ClientReputationHud.score = score;
        ClientReputationHud.nextThreshold = nextThreshold;
        ClientReputationHud.chief = chief;
    }

    public static boolean hasVillage() {
        return hasVillage;
    }

    public static int tierOrdinal() {
        return tierOrdinal;
    }

    public static int score() {
        return score;
    }

    public static int nextThreshold() {
        return nextThreshold;
    }

    public static boolean isChief() {
        return chief;
    }
}
