package net.finnigan.tommemod.entity.custom.EndDrayk;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Mutable pos/yaw/pitch struct for one segment. One is allocated per segment and reused every tick - the
 * trail is sampled ten times a tick per drayk, so this deliberately avoids returning fresh objects.
 *
 * <p>Each field is double-buffered against the previous tick. The trail only advances at 20Hz while the
 * entity itself renders interpolated, so the renderer has to lerp segments the same way or the body
 * visibly steps along behind a smoothly moving head.
 */
public final class DraykPose {
    public Vec3 pos = Vec3.ZERO;
    public float yaw;
    public float pitch;

    public Vec3 prevPos = Vec3.ZERO;
    public float prevYaw;
    public float prevPitch;

    /** Advance to this tick's value, keeping the last one for partial-tick interpolation. */
    public void set(Vec3 pos, float yaw, float pitch) {
        this.prevPos = this.pos;
        this.prevYaw = this.yaw;
        this.prevPitch = this.pitch;

        this.pos = pos;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    /** Collapse both buffers onto the current value, so a freshly placed segment doesn't lerp in. */
    public void snapPrev() {
        this.prevPos = this.pos;
        this.prevYaw = this.yaw;
        this.prevPitch = this.pitch;
    }

    public Vec3 lerpPos(float partialTick)    { return this.prevPos.lerp(this.pos, partialTick); }
    public float lerpYaw(float partialTick)   { return Mth.rotLerp(partialTick, this.prevYaw, this.yaw); }
    public float lerpPitch(float partialTick) { return Mth.rotLerp(partialTick, this.prevPitch, this.pitch); }
}
