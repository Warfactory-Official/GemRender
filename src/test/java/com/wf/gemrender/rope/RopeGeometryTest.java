package com.wf.gemrender.rope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Arrays;
import org.joml.Vector3f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The tube is generated, so it is checked by arithmetic. Flywheel culls backfaces by default, which
 * means a face wound the wrong way round does not disappear — it shows you the inside of the far
 * wall, which at rest is the same silhouette and only slightly wrong shading. Three of this project's
 * fixtures shipped broken that way before anyone checked the mesh.
 *
 * <p>The placement below is {@code instance/rope.vert} transcribed: the shader has no test harness,
 * but it and {@link RopeCurve} are the same construction, so a disagreement here is a real one.
 */
class RopeGeometryTest {
    private static final float RADIUS = 0.08f;

    /** Horizontal, vertical (the mooring-chain case), diagonal, and each with and without sag. */
    private static final float[][] ROPES = {
            {0.0f, 20.0f, 0.0f, 9.0f, 20.0f, 0.0f, 0.0f},
            {0.0f, 20.0f, 0.0f, 9.0f, 20.0f, 0.0f, 2.5f},
            {0.0f, 40.0f, 0.0f, 0.0f, 18.0f, 0.0f, 0.0f},
            {0.0f, 40.0f, 0.0f, 0.0f, 18.0f, 0.0f, 0.6f},
            {-3.0f, 12.0f, 4.0f, 5.0f, 3.0f, -2.0f, 1.75f},
    };

    @Test
    @DisplayName("the vertex count is the quads it actually writes")
    void vertexCount() {
        for (int sides : new int[] {3, 4, 6, 12}) {
            for (int rings : new int[] {1, 4, 16}) {
                assertThat(RopeGeometry.vertices(rings, sides, true))
                        .hasSize(RopeGeometry.vertexCount(rings, sides, true) * RopeGeometry.STRIDE);
                assertThat(RopeGeometry.vertexCount(rings, sides, true))
                        .isEqualTo(RopeGeometry.vertexCount(rings, sides, false) + 8 * sides);
            }
        }
    }

    @Test
    @DisplayName("a rope needs a ring and three sides")
    void rejectsDegenerateTessellation() {
        assertThat(catching(() -> RopeGeometry.vertices(0, 4, true))).isTrue();
        assertThat(catching(() -> RopeGeometry.vertices(4, 2, true))).isTrue();
    }

    @Test
    @DisplayName("the tube covers the whole rope and nothing beyond it")
    void parameterCoverage() {
        int rings = 8;
        float[] v = RopeGeometry.vertices(rings, 5, true);

        boolean[] seen = new boolean[rings + 1];
        for (int i = 0; i < v.length; i += RopeGeometry.STRIDE) {
            float t = v[i + RopeGeometry.T];

            assertThat(t).isBetween(0.0f, 1.0f);

            int step = Math.round(t * rings);
            assertThat(t).as("every ring sits on a step boundary").isCloseTo((float) step / rings, within(1.0e-5f));
            seen[step] = true;
        }

        for (int step = 0; step <= rings; step++) {
            assertThat(seen[step]).as("ring %s is written", step).isTrue();
        }
    }

    @Test
    @DisplayName("the ring directions are on the unit circle, and the caps are on the axis")
    void ringDirections() {
        float[] v = RopeGeometry.vertices(6, 7, true);

        for (int i = 0; i < v.length; i += RopeGeometry.STRIDE) {
            float dirX = v[i + RopeGeometry.DIR_X];
            float dirY = v[i + RopeGeometry.DIR_Y];
            float capSign = v[i + RopeGeometry.CAP_SIGN];
            float radius = (float) Math.sqrt(dirX * dirX + dirY * dirY);

            assertThat(capSign).isIn(-1.0f, 0.0f, 1.0f);

            if (capSign == 0.0f) {
                assertThat(radius).as("a tube vertex is on the rim").isCloseTo(1.0f, within(1.0e-5f));
            } else {
                assertThat(radius).as("a cap vertex is on the rim or on the axis")
                        .satisfiesAnyOf(r -> assertThat(r).isCloseTo(1.0f, within(1.0e-5f)),
                                r -> assertThat(r).isCloseTo(0.0f, within(1.0e-5f)));
            }
        }
    }

    @Test
    @DisplayName("the cap sign says which end a cap is, and only the ends have one")
    void capSigns() {
        float[] v = RopeGeometry.vertices(4, 4, true);

        for (int i = 0; i < v.length; i += RopeGeometry.STRIDE) {
            float t = v[i + RopeGeometry.T];
            float capSign = v[i + RopeGeometry.CAP_SIGN];

            if (capSign < 0.0f) {
                assertThat(t).isZero();
            } else if (capSign > 0.0f) {
                assertThat(t).isEqualTo(1.0f);
            }
        }
    }

    @Test
    @DisplayName("an uncapped tube writes no cap vertices at all")
    void uncapped() {
        float[] v = RopeGeometry.vertices(4, 4, false);

        for (int i = 0; i < v.length; i += RopeGeometry.STRIDE) {
            assertThat(v[i + RopeGeometry.CAP_SIGN]).isZero();
        }
    }

    @Test
    @DisplayName("every face points out of the rope, on every shape of rope")
    void windingFacesOutward() {
        for (int sides : new int[] {3, 4, 8}) {
            float[] v = RopeGeometry.vertices(6, sides, true);

            for (float[] rope : ROPES) {
                int quads = v.length / RopeGeometry.STRIDE / 4;

                for (int quad = 0; quad < quads; quad++) {
                    check(v, quad, rope, sides);
                }
            }
        }
    }

    /**
     * Places one quad the way {@code instance/rope.vert} does and asserts its geometric normal agrees
     * with where that face is supposed to look.
     */
    private void check(float[] v, int quad, float[] rope, int sides) {
        Vector3f[] corner = new Vector3f[4];
        Vector3f[] outward = new Vector3f[4];

        float capSign = 0.0f;
        for (int c = 0; c < 4; c++) {
            int base = (quad * 4 + c) * RopeGeometry.STRIDE;

            float t = v[base + RopeGeometry.T];
            float dirX = v[base + RopeGeometry.DIR_X];
            float dirY = v[base + RopeGeometry.DIR_Y];
            capSign = v[base + RopeGeometry.CAP_SIGN];

            Vector3f[] basis = {new Vector3f(), new Vector3f(), new Vector3f()};
            RopeCurve.frame(rope[0], rope[1], rope[2], rope[3], rope[4], rope[5], rope[6], t, basis);

            Vector3f radial = new Vector3f(basis[1]).mul(dirX).fma(dirY, basis[2]);

            corner[c] = RopeCurve.point(new Vector3f(),
                    rope[0], rope[1], rope[2], rope[3], rope[4], rope[5], rope[6], t)
                    .fma(RADIUS, radial);

            outward[c] = capSign == 0.0f ? radial : new Vector3f(basis[0]).mul(capSign);
        }

        Vector3f normal = geometricNormal(corner);
        if (normal == null) {
            // Wholly degenerate quads would draw nothing; none should exist.
            throw new AssertionError("quad " + quad + " has no area on rope " + Arrays.toString(rope));
        }

        Vector3f expected = new Vector3f();
        for (Vector3f each : outward) {
            expected.add(each);
        }
        expected.normalize();

        assertThat(normal.dot(expected))
                .as("quad %s (capSign %s, %s sides) faces out", quad, capSign, sides)
                .isGreaterThan(0.5f);
    }

    /**
     * The normal of whichever of a quad's two triangles has area — a cap quad has its outer corner
     * doubled, so one of them is always flat.
     */
    private static Vector3f geometricNormal(Vector3f[] corner) {
        int[][] triangles = {{0, 1, 2}, {0, 2, 3}};

        for (int[] triangle : triangles) {
            Vector3f edge1 = new Vector3f(corner[triangle[1]]).sub(corner[triangle[0]]);
            Vector3f edge2 = new Vector3f(corner[triangle[2]]).sub(corner[triangle[0]]);
            Vector3f normal = edge1.cross(edge2);

            if (normal.length() > 1.0e-9f) {
                return normal.normalize();
            }
        }
        return null;
    }

    @Test
    @DisplayName("the texture runs once around the rope and once along it")
    void textureCoordinates() {
        float[] v = RopeGeometry.vertices(4, 6, true);

        float maxU = 0.0f;
        for (int i = 0; i < v.length; i += RopeGeometry.STRIDE) {
            float u = v[i + RopeGeometry.U];
            float texV = v[i + RopeGeometry.V];

            assertThat(u).isBetween(0.0f, 1.0f);
            assertThat(texV).isEqualTo(v[i + RopeGeometry.T]);
            maxU = Math.max(maxU, u);
        }

        assertThat(maxU).as("the seam reaches the far edge of the texture").isEqualTo(1.0f);
    }

    private static boolean catching(Runnable action) {
        try {
            action.run();
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }
}
