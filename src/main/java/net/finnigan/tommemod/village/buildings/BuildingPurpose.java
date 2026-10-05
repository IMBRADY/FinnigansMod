package net.finnigan.tommemod.village.buildings;

/**
 * The jobs a finished building can do for its village, as named by a blueprint's {@code "purpose"}.
 * A building without one is still a building - it just has no effect beyond the blocks.
 */
public final class BuildingPurpose {

    /** Each standing Observatory widens the Chief Desk map. */
    public static final String OBSERVATORY = "observatory";
    /** Houses the village's Warriors: one per bed, sleeping in shifts, kitted out from the chest by the bed. */
    public static final String BARRACKS = "barracks";
    /** Holds the village's wealth; while one stands, village costs are paid from it. */
    public static final String BANK = "bank";
    /** Farmers work these first, and their harvests feed the bank. */
    public static final String FARM = "farm";
    /** Wall pieces and gatehouses: once they close a loop, the ring they make is the village's boundary. */
    public static final String WALL = "wall";

    private BuildingPurpose() {
    }
}
