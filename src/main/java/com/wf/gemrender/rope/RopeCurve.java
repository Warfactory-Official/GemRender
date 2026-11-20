package com.wf.gemrender.rope;

import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * The curve a rope hangs in, and the Java half of {@code rope.glsl}.
 *
 * <p>A rope is a straight line between its endpoints plus a quadratic sag along -Y:
 * {@code P(t) = A + (B - A) t + sag * 4t(1 - t) * -Y}. That is a parabola, which is the shape a
 * uniformly loaded cable takes and the standard approximation to a catenary.
 *
 * <p><b>It is deliberately not a catenary.</b> The closed-form catenary is a function of horizontal
 * distance, so it has no value at all when the two endpoints are vertically above one another — and
 * a mooring chain, the case this was written for, is exactly vertical. The parabola is defined for
 * every configuration, costs four multiply-adds instead of a {@code cosh} and a Newton solve, and at
 * the sag ratios anything in a game uses the two differ by well under a pixel.
 *
 * <p>Everything here is {@code float}, and in the same order as the shader does it, so a value
 * predicted on the CPU matches the one drawn rather than merely rounding to it.
 */
public final class RopeCurve {
    /** Arc length is integrated with this many Gauss-Legendre points. Exact to float at 8. */
    private static final int QUADRATURE = 8;

    private static final double[] GL_X = {
            -0.9602898564975363, -0.7966664774136267, -0.5255324099163290, -0.1834346424956498,
            0.1834346424956498, 0.5255324099163290, 0.7966664774136267, 0.9602898564975363};

    private static final double[] GL_W = {
            0.1012285362903763, 0.2223810344533745, 0.3137066458778873, 0.3626837833783620,
            0.3626837833783620, 0.3137066458778873, 0.2223810344533745, 0.1012285362903763};

    /** Above this many halvings the bisection has run out of float to move. */
    private static final int BISECTIONS = 40;

    private RopeCurve() {
    }

    /**
     * Where the rope is at {@code t}, with no sway. Writes into {@code out} and returns it.
     */
    public static Vector3f point(Vector3f out, float ax, float ay, float az,
                                 float bx, float by, float bz, float sag, float t) {
        float drop = sag * 4.0f * t * (1.0f - t);
        return out.set(ax + (bx - ax) * t, ay + (by - ay) * t - drop, az + (bz - az) * t);
    }

    /**
     * The unnormalised tangent at {@code t}. The sway term is left out of it: at any radius a rope is
     * drawn at, tilting the ring by the sway's slope is invisible.
     */
    public static Vector3f tangent(Vector3f out, float ax, float ay, float az,
                                   float bx, float by, float bz, float sag, float t) {
        return out.set(bx - ax, (by - ay) - sag * 4.0f * (1.0f - 2.0f * t), bz - az);
    }

    /**
     * The twist-free frame across the rope at {@code t}: {@code out[0]} along it, {@code out[1]} and
     * {@code out[2]} across it. The same construction as {@code gemrender_ropeFrame}, including the
     * fallback where the rope itself is vertical and {@code cross(tangent, up)} has no direction —
     * which is the case a mooring chain is in for its whole life.
     */
    public static void frame(float ax, float ay, float az, float bx, float by, float bz,
                             float sag, float t, Vector3f[] out) {
        Vector3f forward = tangent(out[0], ax, ay, az, bx, by, bz, sag, t);

        float len = forward.length();
        if (len > 1.0e-6f) {
            forward.div(len);
        } else {
            forward.set(0.0f, 1.0f, 0.0f);
        }

        Vector3f reference = Math.abs(forward.y) > 0.999f
                ? new Vector3f(1.0f, 0.0f, 0.0f)
                : new Vector3f(0.0f, 1.0f, 0.0f);

        forward.cross(reference, out[1]).normalize();
        out[1].cross(forward, out[2]);
    }

    /**
     * How long the rope actually is, following the curve.
     */
    public static float length(float ax, float ay, float az,
                               float bx, float by, float bz, float sag) {
        double dx = bx - ax;
        double dy = by - ay;
        double dz = bz - az;
        double flat = dx * dx + dz * dz;

        double total = 0.0;
        for (int i = 0; i < QUADRATURE; i++) {
            double t = 0.5 * (GL_X[i] + 1.0);
            double slope = dy - sag * 4.0 * (1.0 - 2.0 * t);
            total += GL_W[i] * Math.sqrt(flat + slope * slope);
        }
        return (float) (total * 0.5);
    }

    /**
     * The sag that makes the rope {@code wanted} blocks long, or zero if it is already that long or
     * longer when straight.
     *
     * <p>Arc length is strictly increasing in sag, so this bisects rather than iterating Newton on a
     * derivative that has to be integrated too. Bisection always answers.
     *
     * <p><b>Meaningless as the rope approaches vertical, and it will not say so.</b> The sag is along
     * -Y, so on a rope whose ends share a column it moves points <em>along</em> the rope rather than
     * away from it: the length is insensitive to it until the curve doubles back, and then jumps. A
     * 20-block vertical rope asked for 1% of slack is handed a sag of 5.7 — which is length-correct,
     * draws in exactly the same place, and crams half the texture's repeats into the bottom fifth.
     * For anything hanging straight down, including a mooring, set {@link RopeInstance#sag} directly;
     * zero is right for a taut one and the shape is identical either way.
     */
    public static float sagForLength(float ax, float ay, float az,
                                     float bx, float by, float bz, float wanted) {
        float chord = chord(ax, ay, az, bx, by, bz);
        if (!(wanted > chord)) {
            return 0.0f;
        }

        // Grow the bracket until it is long enough. Doubling terminates: length grows without bound.
        float high = Math.max(wanted - chord, 1.0e-3f);
        while (length(ax, ay, az, bx, by, bz, high) < wanted && high < 1.0e6f) {
            high *= 2.0f;
        }

        float low = 0.0f;
        for (int i = 0; i < BISECTIONS; i++) {
            float mid = (low + high) * 0.5f;
            if (mid <= low || mid >= high) {
                break;
            }
            if (length(ax, ay, az, bx, by, bz, mid) < wanted) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return (low + high) * 0.5f;
    }

    /**
     * The sag for a rope {@code slack} times its own straight-line distance. 1.0 is taut.
     *
     * <p>Carries {@link #sagForLength}'s caveat about near-vertical ropes.
     */
    public static float sagForSlack(float ax, float ay, float az,
                                    float bx, float by, float bz, float slack) {
        return sagForLength(ax, ay, az, bx, by, bz,
                chord(ax, ay, az, bx, by, bz) * Math.max(slack, 1.0f));
    }

    public static float chord(float ax, float ay, float az, float bx, float by, float bz) {
        float dx = bx - ax;
        float dy = by - ay;
        float dz = bz - az;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Where the curve hangs lowest. Not {@code t = 0.5} unless the endpoints are level: the sag is
     * symmetric but the chord it is subtracted from is not.
     *
     * <p>A rope with no sag is a straight line, and the lowest point of a line is an end of it. Said
     * separately because the general formula divides by the sag: it wants {@code t} at plus or minus
     * infinity there, which clamps to the right end, but only after passing through a division by
     * zero. Answering {@code t = 0.5} instead, as this did, returns the <em>midpoint</em> of a taut
     * rope, and the only thing that reads this is {@link #sphere}: a mooring chain hangs taut and
     * vertical, so its cull sphere covered the top half of it and was half the radius it needed.
     */
    public static float lowestY(float ay, float by, float sag) {
        if (!(sag > 1.0e-6f)) {
            return Math.min(ay, by);
        }
        float t = 0.5f - (by - ay) / (8.0f * sag);
        t = Math.min(1.0f, Math.max(0.0f, t));
        return ay + (by - ay) * t - sag * 4.0f * t * (1.0f - t);
    }

    /**
     * A sphere containing every vertex the shader can produce, written as centre in xyz and radius
     * in w. Flywheel's own model sphere is useless here for the same reason it is useless for a
     * skinned model: the mesh's vertices are curve parameters, not positions.
     *
     * <p>Pass the widest the rope ever gets — its own radius plus the sway amplitude — or the
     * indirect backend culls it away at the edges of the screen and nothing says so.
     */
    public static void sphere(Vector4f out, float ax, float ay, float az,
                              float bx, float by, float bz, float sag, float margin) {
        float lowest = lowestY(ay, by, sag);
        float top = Math.max(ay, by);

        float cx = (ax + bx) * 0.5f;
        float cy = (lowest + top) * 0.5f;
        float cz = (az + bz) * 0.5f;

        // Every point of the curve is inside the box spanned by the endpoints and the lowest point,
        // and the corner of that box is the furthest anything can be from its centre.
        float hx = Math.abs(bx - ax) * 0.5f;
        float hy = (top - lowest) * 0.5f;
        float hz = Math.abs(bz - az) * 0.5f;

        out.set(cx, cy, cz, (float) Math.sqrt(hx * hx + hy * hy + hz * hz) + margin);
    }
}
