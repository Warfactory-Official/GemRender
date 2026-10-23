package com.wf.gemrender.rope;

import dev.engine_room.flywheel.api.instance.InstanceHandle;
import dev.engine_room.flywheel.api.instance.InstanceType;
import dev.engine_room.flywheel.lib.instance.ColoredLitInstance;
import dev.engine_room.flywheel.lib.util.ExtraMemoryOps;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;


public class RopeInstance extends ColoredLitInstance {
    /** The light at the far end. The near end uses the inherited {@code light}. */
    public int lightB = 0;

    public final Vector3f a = new Vector3f();

    public final Vector3f b = new Vector3f();

    /** sag, radius, uvScale, twist. */
    public final Vector4f shape = new Vector4f(0.0f, 0.05f, 1.0f, 0.0f);

    /** amplitude, frequency in radians per second, phase, waves along the rope. */
    public final Vector4f sway = new Vector4f(0.0f, 0.0f, 0.0f, 1.0f);

    public final Vector4f sphere = new Vector4f(0.0f, 0.0f, 0.0f, 1.0f);

    public RopeInstance(InstanceType<? extends RopeInstance> type, InstanceHandle handle) {
        super(type, handle);
    }

    public RopeInstance between(float ax, float ay, float az, float bx, float by, float bz) {
        a.set(ax, ay, az);
        b.set(bx, by, bz);
        return refresh();
    }

    public RopeInstance between(Vector3f from, Vector3f to) {
        return between(from.x, from.y, from.z, to.x, to.y, to.z);
    }

    /**
     * How far the rope droops below the straight line between its ends, in blocks, at its deepest.
     */
    public RopeInstance sag(float sag) {
        shape.x = Math.max(0.0f, sag);
        return write();
    }

    /**
     * Sets the sag so the rope is {@code slack} times as long as the straight line between its ends.
     * 1.0 is taut. Solved here rather than in the shader: the endpoints move at most once a frame and
     * the answer is the same for all several hundred vertices.
     */
    public RopeInstance slack(float slack) {
        return sag(RopeCurve.sagForSlack(a.x, a.y, a.z, b.x, b.y, b.z, slack));
    }

    /** Sets the sag so the rope is exactly {@code length} blocks of rope, however its ends move. */
    public RopeInstance length(float length) {
        return sag(RopeCurve.sagForLength(a.x, a.y, a.z, b.x, b.y, b.z, length));
    }

    public RopeInstance radius(float radius) {
        shape.y = radius;
        return write();
    }

    /** How many times the texture repeats along the rope. */
    public RopeInstance tiling(float repeats) {
        shape.z = repeats;
        return write();
    }

    /** Full turns of the ring about the rope's own axis, end to end. */
    public RopeInstance twist(float turns) {
        shape.w = turns;
        return write();
    }

    /**
     * A drift perpendicular to the rope, pinned to zero at both ends. {@code phase} is what keeps two
     * ropes side by side from moving as one; derive it from something stable like a block position,
     * not from a random, or it jumps every time the visual is rebuilt.
     */
    public RopeInstance sway(float amplitude, float frequency, float phase, float waves) {
        sway.set(amplitude, frequency, phase, waves);
        return write();
    }

    public RopeInstance lightB(int light) {
        this.lightB = light;
        return this;
    }

    /** Both ends lit the same. */
    public RopeInstance litUniformly(int light) {
        this.light = light;
        this.lightB = light;
        return this;
    }


    private RopeInstance write() {
        return this;
    }

    /**
     * Recomputes the cull sphere from the current endpoints, sag, radius and sway.
     */
    public RopeInstance refresh() {
        RopeCurve.sphere(sphere, a.x, a.y, a.z, b.x, b.y, b.z, shape.x, shape.y + sway.x);
        return write();
    }
}
