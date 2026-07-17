package net.finnigan.tommemod.entity.custom.Bosses.BossCrab;

public enum CrabAttackType {

    // animationName, durationTicks, hitTicks[], hitDamage[], shape, coneRange, coneAngleDeg, cylRadius, verticalReach
    DOUBLEPINCH(
            "doublepinch", 20,
            new int[] { 9, 14 }, // tick when hits
            new double[] { 10, 10 }, // damage
            HitboxShape.CONE, 5.0, 90.0, 0, 3.0
    ),
    BIGPINCH(
            "bigpinch", 26,
            new int[] { 14 }, new double[] { 12 },
            HitboxShape.CONE, 7.0, 90.0, 0, 3.0
    ),
    GROUNDPOUND(
            "groundpound", 26,
            new int[] { 23 }, new double[] { 16 },
            HitboxShape.CYLINDER, 0, 0, 8.0, 3.0
    ),
    POUND_DOWN(
            "pound down", 8,
            new int[] { 5 }, new double[] { 16 },
            HitboxShape.CYLINDER, 0, 0, 8.0, 3.0
    );

    public final String animationName;
    public final int durationTicks;
    public final int[] hitTicks;
    public final double[] hitDamage;
    public final HitboxShape shape;
    public final double coneRange;
    public final double coneAngleDegrees;
    public final double cylinderRadius;
    public final double cylinderVerticalRange; // vertical tolerance for CONE, vertical half-height for CYLINDER

    CrabAttackType(String animationName, int durationTicks, int[] hitTicks, double[] hitDamage,
                   HitboxShape shape, double coneRange, double coneAngleDegrees,
                   double cylinderRadius, double verticalReach) {
        this.animationName = animationName;
        this.durationTicks = durationTicks;
        this.hitTicks = hitTicks;
        this.hitDamage = hitDamage;
        this.shape = shape;
        this.coneRange = coneRange;
        this.coneAngleDegrees = coneAngleDegrees;
        this.cylinderRadius = cylinderRadius;
        this.cylinderVerticalRange = verticalReach;
    }

    public enum HitboxShape {
        CONE,
        CYLINDER
    }
}