package net.finnigan.tommemod.entity.custom.EndDrayk;

/**
 * The ten-piece layout table. The whole chain shape lives here so retuning it is a one-line edit.
 *
 * <p>Declaration order is head-to-tail and is load-bearing: {@link #trailOffset} is accumulated across
 * {@link #values()} in declaration order, and {@code ordinal()} indexes the entity's part and pose arrays.
 */
public enum DraykSegment {
    HEAD   (Kind.HEAD,  "head",   1.2F),
    NECK_1 (Kind.BODY,  "body1",  1.2F),
    NECK_2 (Kind.BODY,  "body2",  1.2F),
    WINGS  (Kind.WINGS, "arms",   1.2F),
    CHEST_1(Kind.BODY,  "body3",  1.1F),
    CHEST_2(Kind.BODY,  "body4",  1.1F),
    LEGS   (Kind.LEGS,  "legs",   1.2F),
    BODY_1 (Kind.BODY,  "body5",  1.0F),
    BODY_2 (Kind.BODY,  "body6",  1.0F),
    BODY_3 (Kind.BODY,  "body7",  1.0F),
    TAIL_1 (Kind.BODY,  "body8",  0.9F),
    TAIL_2 (Kind.BODY,  "body9",  0.9F),
    TAIL_3 (Kind.BODY,  "body10", 0.9F);

    public enum Kind { HEAD, BODY, WINGS, LEGS }

    /**
     * Distance between adjacent segment centres at scale 1.0, in blocks.
     *
     * <p>Derived from the model, not picked: a body cube is 6px deep, i.e. 0.375 blocks, so anything
     * above that leaves the segments visibly disconnected - the plan's original 0.9 would have left
     * 8.4px of empty air between every pair. This sits ~9% under the cube depth so neighbours overlap
     * slightly and no seam opens on the outside of a curve. Raise it toward 0.375 for a beaded look,
     * past it for a chain of separate floating pieces.
     */
    private static final double GAP = 0.34D;

    private final Kind kind;
    private final String boneName;
    private final float scale;
    private double trailOffset;

    DraykSegment(Kind kind, String boneName, float scale) {
        this.kind = kind;
        this.boneName = boneName;
        this.scale = scale;
    }

    static {
        DraykSegment[] all = values();
        double running = 0.0D;
        for (int i = 1; i < all.length; i++) {
            // Average the two scales so spacing tapers with the body instead of leaving
            // gaps where a 1.0 segment meets a 0.8 one.
            running += GAP * (all[i - 1].scale + all[i].scale) * 0.5D;
            all[i].trailOffset = running;
        }
    }

    public Kind kind()          { return this.kind; }
    public String boneName()    { return this.boneName; }
    public float scale()        { return this.scale; }

    /** Distance behind the head, in blocks, that this segment trails at. */
    public double trailOffset() { return this.trailOffset; }

    /**
     * Hitbox footprint, measured off the merged geo rather than guessed. The pieces are small: a body
     * cube is 6x7x6 px, the head 8x7x10, the wing torso 7x8x9. The plan's flat 0.9/1.1 would have wrapped
     * every segment in a box two to three times the geometry inside it, which reads as phantom hits and
     * as players bumping into empty air.
     *
     * <p>The WINGS and LEGS boxes cover their torso only - the wing membranes span roughly four blocks
     * tip to tip and the legs dangle well below the body, and boxing either would wrap the drayk in far
     * more hittable volume than it looks like it has. They are scenery, not surface.
     */
    public float width() {
        return switch (this.kind) {
            case HEAD  -> 0.65F;
            case WINGS -> 0.60F;
            case LEGS  -> 0.55F;
            case BODY  -> 0.40F;
        } * this.scale;
    }

    public float height() {
        return (this.kind == Kind.WINGS ? 0.50F : 0.45F) * this.scale;
    }
}
