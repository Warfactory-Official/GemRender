package com.wf.gemrender.light;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3fc;

/**
 * One frame's lights. World coordinates; {@code intensity} scales {@code rgb} at 1 block before falloff
 * {@code (1 - (d/range)^4)^2 / (d^2 + 1)}.
 */
public interface LightSink {
    /**
     * @param shadow occluded by full opaque blocks within 64 blocks of the camera section. Shadowed light =>
     *               nothing lit past that window; source outside it, or beyond the nearest 16 shadowed => not shaded
     */
    default void point(double x, double y, double z, float range, int rgb, float intensity, boolean shadow) {
        point(x, y, z, range, rgb, intensity, shadow, false);
    }

    /** @param ir infrared: shaded only under {@link Lights.IrView#SPLIT} / {@link Lights.IrView#MERGED} */
    void point(double x, double y, double z, float range, int rgb, float intensity, boolean shadow, boolean ir);

    /**
     * @param softness metres, as {@link #spot}'s softness: floor 1 m, flattens the near field. Sinks that do not
     *                 know it ignore it.
     */
    default void point(double x, double y, double z, float range, int rgb, float intensity, boolean shadow,
                       boolean ir, float softness) {
        point(x, y, z, range, rgb, intensity, shadow, ir);
    }

    /**
     * @param direction unit beam axis
     * @param up        any vector off the axis; orients the cookie
     * @param outerDegrees half angle, {@code < 89}
     * @param shadow       as {@link #point}
     */
    default void spot(double x, double y, double z, Vector3fc direction, Vector3fc up, float range,
                      float innerDegrees, float outerDegrees, int rgb, float intensity, @Nullable LightCookie cookie,
                      boolean shadow) {
        spot(x, y, z, direction, up, range, innerDegrees, outerDegrees, rgb, intensity, cookie, shadow, false);
    }

    /**
     * @param softness metres: the falloff is {@code 1 / (d^2 + softness^2)} (floor 1 m), so a larger value flattens the
     *                 near field: a dim wide skirt around a beam stays visible far off without blowing out up close.
     *                 Sinks that do not know it ignore it.
     */
    default void spot(double x, double y, double z, Vector3fc direction, Vector3fc up, float range,
                      float innerDegrees, float outerDegrees, int rgb, float intensity, @Nullable LightCookie cookie,
                      boolean shadow, boolean ir, float softness) {
        spot(x, y, z, direction, up, range, innerDegrees, outerDegrees, rgb, intensity, cookie, shadow, ir);
    }

    /** @param ir as {@link #point(double, double, double, float, int, float, boolean, boolean)} */
    void spot(double x, double y, double z, Vector3fc direction, Vector3fc up, float range,
              float innerDegrees, float outerDegrees, int rgb, float intensity, @Nullable LightCookie cookie,
              boolean shadow, boolean ir);
}
