package net.finnigan.tommemod.entity.custom.EndDrayk;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Arc-length position history for the drayk's head.
 *
 * <p>Segments are spaced by <em>distance</em>, not by ticks. The obvious implementation - "segment N is
 * where the head was N*4 ticks ago" - makes the body concertina: it bunches up when the drayk slows or
 * turns and stretches when it accelerates. Instead each sample records the total distance the head had
 * travelled when it was taken, so sampling asks for a distance behind the head rather than an age.
 */
public final class DraykTrail {
    private static final int CAPACITY = 256;
    private static final double MIN_STEP = 1.0E-4D;

    private final Vec3[] pos = new Vec3[CAPACITY];
    private final float[] yaw = new float[CAPACITY];
    private final float[] pitch = new float[CAPACITY];
    /** Total distance the head had travelled when this sample was taken. */
    private final double[] odo = new double[CAPACITY];

    private int newest = -1;
    private int count = 0;
    private double travelled = 0.0D;

    public void push(Vec3 p, float yRot, float xRot) {
        if (count > 0) {
            double step = p.distanceTo(pos[newest]);
            if (step < MIN_STEP) return;   // hovering: don't burn samples, body holds its shape
            travelled += step;
        }
        newest = (newest + 1) % CAPACITY;
        pos[newest] = p;
        yaw[newest] = yRot;
        pitch[newest] = xRot;
        odo[newest] = travelled;
        if (count < CAPACITY) count++;
    }

    /** True once at least one sample exists. Segments have nothing to read before that. */
    public boolean isEmpty() {
        return this.count == 0;
    }

    /** Where the head was {@code distance} blocks ago, interpolated between bracketing samples. */
    public void sampleBehind(double distance, DraykPose out) {
        if (count == 0) return;

        double target = travelled - distance;
        int newer = newest;

        for (int i = 1; i < count; i++) {
            int older = Math.floorMod(newest - i, CAPACITY);
            if (odo[older] <= target) {
                double span = odo[newer] - odo[older];
                double t = span < MIN_STEP ? 0.0D : (target - odo[older]) / span;
                out.set(pos[older].lerp(pos[newer], t),
                        Mth.rotLerp((float) t, yaw[older], yaw[newer]),
                        Mth.rotLerp((float) t, pitch[older], pitch[newer]));
                return;
            }
            newer = older;
        }
        // Trail shorter than requested (just spawned): clamp to the oldest sample we have.
        int oldest = Math.floorMod(newest - (count - 1), CAPACITY);
        out.set(pos[oldest], yaw[oldest], pitch[oldest]);
    }
}
