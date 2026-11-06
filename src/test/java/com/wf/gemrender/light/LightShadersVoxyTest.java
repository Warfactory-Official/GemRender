package com.wf.gemrender.light;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Voxy's anchors are one fork away from changing, and a throw during parse fails the world join. */
class LightShadersVoxyTest {
    private static final String VERTEX_ID = "voxy:lod/gl46/quads3.vert";
    private static final String FRAGMENT_ID = "voxy:lod/gl46/quads.frag";
    private static final String VOXY_CORNER = "    vec4 pos = MVP * vec4(point, 1.0f);\n";
    private static final String NEO_VOXY_CORNER =
            "    vec4 pos = MVP * vec4(applyWorldCurvature(getQuadCornerPoint(quad, cornerId)), 1.0f);\n";
    private static final String VERTEX = """
            #version 460 core
            layout(location = 0) out flat uvec4 interData;
            layout(location = 1) out vec2 uv;
            vec4 tinting = getLighting(lighting);
            attributes.z = addin|(face<<8);
            vec4 getQuadCornerPos(in QuadData quad, uint cornerId) {
            %s    return pos;
            }
            void main() {
                gl_Position = (getQuadCornerPos(quad, cornerId));
            }
            """;
    private static final String FRAGMENT = """
            #version 460 core
            layout(binding = 0) uniform sampler2D blockModelAtlas;
            layout(location = 0) out vec4 outColour;
            void main() {
                colour = computeColour(texPos, colour);
                outColour = colour;
            }
            """;

    @Test
    void voxyIsPatched() {
        String vertex = LightShaders.voxy(VERTEX_ID, VERTEX.formatted(VOXY_CORNER));
        assertThat(vertex).contains("_grl_corner = point;", "(lighting<<16)", "layout(location = 2) out vec3 _grl_pos;");
        assertThat(LightShaders.voxy(FRAGMENT_ID, FRAGMENT)).contains("_grl_irOut", "gemrender_lightPrepare(_grl_pos);");
    }

    @Test
    void forkWithAnotherCornerKeepsBothStages() {
        assertSkipped(VERTEX.formatted(NEO_VOXY_CORNER));
    }

    @Test
    void forkUsingLocationTwoKeepsBothStages() {
        assertSkipped(VERTEX.formatted(VOXY_CORNER) + "layout(location = 2) out float boundaryDistanceSquared;\n");
    }

    @Test
    void skipLastsOnlyUntilTheNextVertexStage() {
        assertSkipped(VERTEX.formatted(NEO_VOXY_CORNER));
        voxyIsPatched();
    }

    private static void assertSkipped(String vertex) {
        assertThat(LightShaders.voxy(VERTEX_ID, vertex)).isEqualTo(vertex);
        assertThat(LightShaders.voxy(FRAGMENT_ID, FRAGMENT)).isEqualTo(FRAGMENT);
    }
}
