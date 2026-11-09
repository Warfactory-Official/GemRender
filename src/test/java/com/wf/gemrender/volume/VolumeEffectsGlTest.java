package com.wf.gemrender.volume;

import com.wf.gemrender.gl.HeadlessGl;
import com.wf.gemrender.gl.ShaderSources;
import com.wf.gemrender.Ids;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.lwjgl.opengl.GL46C.*;

/** Draws the shipped instance and material shaders, including their real Java slot layout and noise asset. */
@Tag("gl")
class VolumeEffectsGlTest {
    private static final int SIZE = 128;
    private static final VolumeStyle STYLE = VolumeStyle.builder().density(0.8f).steps(96, 4).build();

    @Test
    void shapesSurviveEnteringTheirBoundsAndTheCameraCenter() throws Exception {
        try (HeadlessGl ignored = HeadlessGl.createOrSkip(); Draw draw = new Draw()) {
            int program = program("#define _FLW_EVALUATE\n");
            Volume neighbor = Volume.create(STYLE).extent(1, 1, 1).heat(1, 20);
            try {
                for (VolumeShape shape : VolumeShape.values()) {
                    Volume volume = Volume.create(STYLE).extent(8, 8, 8).shape(shape)
                            .billow(shape == VolumeShape.SHELL ? 0 : 0.1f).heat(0.95f, 2).animate(3, 0.4f);
                    try {
                        draw.volume(volume);
                        for (float z : new float[]{16, 8.001f, 8, 7.999f, 0}) {
                            for (float tilt : new float[]{0, 0.02f}) {
                                draw.frame(program, z, 1000, tilt);
                                float[] smoke = draw.pixel(0);
                                float[] fire = draw.pixel(1);
                                for (float f : smoke) assertThat(Float.isFinite(f)).as("%s at z=%s", shape, z).isTrue();
                                for (float f : fire) assertThat(Float.isFinite(f)).as("%s emission at z=%s", shape, z).isTrue();
                                assertThat(smoke[3]).as("%s visible at z=%s", shape, z).isGreaterThan(0.01f);
                                if (shape == VolumeShape.FALLOUT) {
                                    assertThat(fire[0]).isZero();
                                } else {
                                    assertThat(fire[0]).as("%s keeps fire at z=%s", shape, z).isGreaterThan(0.0f);
                                }
                            }
                        }
                    } finally {
                        volume.close();
                    }
                }
            } finally {
                neighbor.close();
                glDeleteProgram(program);
            }
        }
    }

    @Test
    void nearbyWallsClipTheMarchWithoutHidingSmokeInFrontOfThem() throws Exception {
        try (HeadlessGl ignored = HeadlessGl.createOrSkip(); Draw draw = new Draw()) {
            int program = program("#define _FLW_EVALUATE\n");
            Volume volume = Volume.create(STYLE).extent(8, 8, 8).billow(0).heat(0.9f, 2);
            try {
                draw.volume(volume);
                draw.frame(program, 16, 0.5f);
                assertThat(draw.pixel(0)[3]).isZero();
                assertThat(draw.pixel(1)[0]).isZero();

                draw.frame(program, 0, 1000);
                float unobstructed = draw.pixel(0)[3];
                draw.frame(program, 0, 0.5f);
                assertThat(draw.pixel(0)[3]).isBetween(0.01f, unobstructed - 0.01f);
                assertThat(draw.pixel(1)[0]).isGreaterThan(0);

                volume.shape(VolumeShape.SHELL);
                draw.volume(volume);
                draw.frame(program, 0, 0.5f);
                assertThat(draw.pixel(0)[3]).isZero();
                draw.frame(program, 0, 1000);
                assertThat(draw.pixel(0)[3]).isGreaterThan(0.01f);

                volume.heat(0, 2);
                draw.volume(volume);
                draw.frame(program, 0, 1000);
                assertThat(draw.pixel(1)[0]).isZero();
            } finally {
                volume.close();
                glDeleteProgram(program);
            }
        }
    }

    @Test
    void materialCompilesForEveryTransparencyPass() {
        try (HeadlessGl ignored = HeadlessGl.createOrSkip()) {
            for (String mode : new String[]{"", "#define _FLW_DEPTH_RANGE\n", "#define _FLW_COLLECT_COEFFS\n",
                    "#define _FLW_EVALUATE\n", "#define _FLW_EVALUATE\n#define GEMRENDER_CHUNK_OIT\n",
                    "#define _FLW_COLLECT_COEFFS\n#define GEMRENDER_CHUNK_OIT\n"}) {
                glDeleteProgram(program(mode));
            }
        }
    }

    @Test
    void emissionIsNotExtinguishedTwiceByItsOwnSmoke() throws Exception {
        try (HeadlessGl ignored = HeadlessGl.createOrSkip(); Draw draw = new Draw()) {
            int evaluate = program("#define _FLW_EVALUATE\n");
            int forward = program("");
            Volume volume = Volume.create(STYLE).extent(8, 8, 8).heat(0.9f, 2);
            try {
                draw.volume(volume);
                draw.frame(evaluate, 16, 1000);
                float[] smoke = draw.pixel(0);
                float[] fire = draw.pixel(1);
                assertThat(fire[0]).as("HDR emission remains above display white").isGreaterThan(1.0f);
                draw.frame(forward, 16, 1000);
                float[] combined = draw.pixel(0);
                float transmission = (float) Math.exp(-smoke[3]);
                for (int c = 0; c < 3; c++) {
                    float composited = smoke[c] * (1 - transmission) + fire[c] * transmission;
                    assertThat(composited).isCloseTo(combined[c] * combined[3],
                            org.assertj.core.api.Assertions.within(0.001f));
                }
            } finally {
                volume.close();
                glDeleteProgram(evaluate);
                glDeleteProgram(forward);
            }
        }
    }

    @Test
    void fabulousUsesTheEffectDepthRatherThanTheNearPlaneQuad() throws Exception {
        try (HeadlessGl ignored = HeadlessGl.createOrSkip(); Draw draw = new Draw()) {
            int program = program("#define _FLW_DEPTH_RANGE\n");
            Volume volume = Volume.create(STYLE).extent(8, 8, 8).billow(0);
            try {
                draw.volume(volume);
                draw.frame(program, 16, 1000);
                assertThat(draw.pixel(0)[0]).isBetween(9.0f, 11.0f);
                draw.frame(program, 0, 1000);
                assertThat(draw.pixel(0)[0]).isBetween(0.0f, 0.1f);
            } finally {
                volume.close();
                glDeleteProgram(program);
            }
        }
    }

    @Test
    void parallelRaysOnBoxFacesHaveFiniteIntervals() throws Exception {
        try (HeadlessGl gl = HeadlessGl.createOrSkip(); Draw draw = new Draw()) {
            Volume volume = Volume.create(STYLE).extent(8, 8, 8);
            String source = """
                    #version 460 core
                    layout(local_size_x = 1) in;
                    layout(std430, binding = 0) writeonly buffer Out { vec2 result[3]; };
                    uniform int uSlot;
                    uniform float flw_renderSeconds;
                    uniform mat4 flw_viewInverse;
                    uniform vec3 flw_light0Direction;
                    #define gl_FragCoord vec4(0.5)
                    """ + ShaderSources.inlined("volume_march.glsl") + """
                    void main() {
                        GemRenderVolume v = gemrender_volume(uint(uSlot));
                        result[0] = gemrender_volumeSpan(v, vec3(0), vec3(8,0,0), vec3(0,0,-1));
                        result[1] = gemrender_volumeSpan(v, vec3(0), vec3(8.01,0,0), vec3(0,0,-1));
                        result[2] = gemrender_volumeSpan(v, vec3(0), vec3(0,0,16), vec3(0,0,-1));
                    }
                    """;
            int program = gl.computeProgram(source);
            int output = glGenBuffers();
            try {
                draw.volume(volume);
                glUseProgram(program);
                HeadlessGl.samplerUnit(program, "_gemrender_volumes", 0);
                glUniform1i(glGetUniformLocation(program, "uSlot"), volume.slot());
                glBindBuffer(GL_SHADER_STORAGE_BUFFER, output);
                glBufferData(GL_SHADER_STORAGE_BUFFER, 6L * Float.BYTES, GL_STATIC_READ);
                glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, output);
                glDispatchCompute(1, 1, 1);
                glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
                float[] result = new float[6];
                glGetBufferSubData(GL_SHADER_STORAGE_BUFFER, 0, result);
                assertThat(result).containsExactly(0, 8, 1, 0, 8, 24);
            } finally {
                volume.close();
                glDeleteBuffers(output);
                glDeleteProgram(program);
            }
        }
    }

    @Test
    void energyHasADarkBodyBrightLimbAndConsistentOpticalSize() throws Exception {
        try (HeadlessGl ignored = HeadlessGl.createOrSkip(); Draw draw = new Draw()) {
            int shader = program("#define _FLW_EVALUATE\n");
            var energy = VolumeStyle.builder().density(1.8f).tint(0x030C24).steps(64, 0).build();
            float previousAlpha = -1;
            try {
                for (float scale : new float[]{1, 8}) {
                    Volume volume = Volume.create(energy).extent(8 * scale, 8 * scale, 8 * scale)
                            .shape(VolumeShape.ENERGY).heat(0.8f, 0.8f).animate(2, 0.4f);
                    try {
                        draw.volume(volume);
                        draw.frame(shader, 24 * scale, 1000);
                        if (scale == 1) draw.preview("energy-reference");
                        float[] center = draw.pixel(0);
                        float[] fire = draw.pixel(1);
                        float body = fire[2] * (float) Math.exp(-center[3]);
                        float[] edge = draw.pixelAt(0, SIZE / 2 + 23, SIZE / 2);
                        float[] rim = draw.pixelAt(1, SIZE / 2 + 23, SIZE / 2);
                        float limb = rim[2] * (float) Math.exp(-edge[3]);
                        assertThat(limb).as("blue rim must remain brighter than the body").isGreaterThan(body * 1.5f);
                        if (previousAlpha >= 0) assertThat(center[3]).isCloseTo(previousAlpha,
                                org.assertj.core.api.Assertions.within(0.005f));
                        previousAlpha = center[3];
                    } finally { volume.close(); }
                }
                var smoke = VolumeStyle.builder().density(0.22f).tint(0x514A43).ambient(0.16f).steps(96, 4).build();
                for (float heat : new float[]{1, 0.3f}) {
                    Volume volume = Volume.create(smoke).extent(8, 8, 8).shape(VolumeShape.ELLIPSOID)
                            .heat(heat, 1.6f).billow(0.14f).animate(2, 0.4f);
                    try {
                        draw.volume(volume);
                        draw.frame(shader, 24, 1000);
                        draw.preview(heat == 1 ? "fireball-hot" : "fireball-cooling");
                    } finally { volume.close(); }
                }
            } finally { glDeleteProgram(shader); }
        }
    }

    private static int program(String mode) {
        String vertex = """
                #version 460 core
                uniform mat4 flw_viewProjection;
                uniform mat4 flw_viewProjectionInverse;
                uniform mat4 flw_viewInverse;
                uniform int uSlot;
                struct FlwInstance { vec3 center; uint volume; };
                out vec4 flw_vertexPos;
                out vec4 flw_vertexColor;
                out vec3 flw_vertexNormal;
                flat out ivec2 flw_vertexOverlay;
                out vec2 flw_vertexLight;
                """ + ShaderSources.inlined("instance/volume.vert") + """
                void main() {
                    const vec2 corners[6] = vec2[6](vec2(-0.5,-0.5),vec2(0.5,-0.5),vec2(0.5,0.5),
                        vec2(-0.5,-0.5),vec2(0.5,0.5),vec2(-0.5,0.5));
                    flw_vertexPos = vec4(corners[gl_VertexID], 0.0, 1.0);
                    flw_instanceVertex(FlwInstance(vec3(0.0), uint(uSlot)));
                    gl_Position = flw_viewProjection * flw_vertexPos;
                }
                """;
        String fragment = "#version 460 core\n" + mode + """
                uniform mat4 flw_viewInverse;
                uniform mat4 flw_viewProjection;
                uniform vec3 flw_cameraPos;
                uniform vec3 flw_light0Direction;
                uniform float flw_renderSeconds;
                uniform sampler2D flw_diffuseTex;
                in vec4 flw_vertexPos;
                in vec4 flw_vertexColor;
                flat in ivec2 flw_vertexOverlay;
                ivec2 flw_fragOverlay;
                vec4 flw_fragColor;
                layout(location = 0) out vec4 color;
                """ + ShaderSources.inlined("material/volume_effect.frag") + """
                void main() {
                    flw_fragOverlay = flw_vertexOverlay;
                    flw_materialFragment();
                #ifdef _FLW_DEPTH_RANGE
                """ + VolumeShaders.patch(Ids.of("flywheel", "internal/common.frag"), "float linearDepth = linear_depth();") + """
                    color = vec4(linearDepth);
                #else
                    color = flw_fragColor;
                #endif
                }
                """;
        int vs = shader(GL_VERTEX_SHADER, vertex);
        int fs = shader(GL_FRAGMENT_SHADER, fragment);
        int program = glCreateProgram();
        try {
            glAttachShader(program, vs);
            glAttachShader(program, fs);
            glLinkProgram(program);
            assertThat(glGetProgrami(program, GL_LINK_STATUS)).withFailMessage(glGetProgramInfoLog(program)).isEqualTo(GL_TRUE);
            return program;
        } finally {
            glDeleteShader(vs);
            glDeleteShader(fs);
        }
    }

    private static int shader(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        assertThat(glGetShaderi(shader, GL_COMPILE_STATUS)).withFailMessage(glGetShaderInfoLog(shader)).isEqualTo(GL_TRUE);
        return shader;
    }

    private static void matrix(int program, String uniform, Matrix4f matrix) {
        glUniformMatrix4fv(glGetUniformLocation(program, uniform), false, matrix.get(new float[16]));
    }

    private static final class Draw implements AutoCloseable {
        private final int vao = glGenVertexArrays();
        private final int fbo = glGenFramebuffers();
        private final int buffer = glGenBuffers();
        private final int records = glGenTextures();
        private final int noise = glGenTextures();
        private final int scene = glGenTextures();
        private final int[] outputs = {glGenTextures(), glGenTextures()};
        private int slot;

        Draw() throws Exception {
            glBindFramebuffer(GL_FRAMEBUFFER, fbo);
            for (int i = 0; i < outputs.length; i++) {
                glBindTexture(GL_TEXTURE_2D, outputs[i]);
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA32F, SIZE, SIZE, 0, GL_RGBA, GL_FLOAT, (ByteBuffer) null);
                glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + i, GL_TEXTURE_2D, outputs[i], 0);
            }
            glDrawBuffers(new int[]{GL_COLOR_ATTACHMENT0, GL_COLOR_ATTACHMENT1});
            assertThat(glCheckFramebufferStatus(GL_FRAMEBUFFER)).isEqualTo(GL_FRAMEBUFFER_COMPLETE);
            BufferedImage image;
            try (var in = getClass().getResourceAsStream("/assets/gemrender/textures/fx/volume_noise.png")) {
                image = ImageIO.read(in);
            }
            assertThat(image.getWidth()).isEqualTo(528);
            assertThat(image.getHeight()).isEqualTo(528);
            ByteBuffer pixels = MemoryUtil.memAlloc(528 * 528 * 4);
            try {
                for (int y = 0; y < 528; y++) {
                    for (int x = 0; x < 528; x++) {
                        int rgb = image.getRGB(x, y);
                        pixels.put((byte) (rgb >> 16)).put((byte) (rgb >> 8)).put((byte) rgb).put((byte) (rgb >> 24));
                    }
                }
                pixels.flip();
                glActiveTexture(GL_TEXTURE1);
                glBindTexture(GL_TEXTURE_2D, noise);
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 528, 528, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            } finally {
                MemoryUtil.memFree(pixels);
            }
        }

        void volume(Volume volume) throws Exception {
            slot = volume.slot();
            var field = VolumeBuffer.class.getDeclaredField("data");
            field.setAccessible(true);
            float[] data = (float[]) field.get(VolumeBuffer.getInstance());
            glBindBuffer(GL_TEXTURE_BUFFER, buffer);
            glBufferData(GL_TEXTURE_BUFFER, data, GL_STATIC_DRAW);
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_BUFFER, records);
            glTexBuffer(GL_TEXTURE_BUFFER, GL_RGBA32F, buffer);
        }

        void frame(int program, float cameraZ, float sceneDistance) {
            frame(program, cameraZ, sceneDistance, 0);
        }

        void frame(int program, float cameraZ, float sceneDistance, float tilt) {
            glBindFramebuffer(GL_FRAMEBUFFER, fbo);
            glViewport(0, 0, SIZE, SIZE);
            glDisable(GL_BLEND);
            glDisable(GL_DEPTH_TEST);
            glDisable(GL_CULL_FACE);
            glClearBufferfv(GL_COLOR, 0, new float[4]);
            glClearBufferfv(GL_COLOR, 1, new float[4]);
            glUseProgram(program);
            Matrix4f view = new Matrix4f().rotateZ(tilt).rotateX(tilt).translate(0, 0, -cameraZ);
            Matrix4f projection = new Matrix4f().perspective((float) Math.toRadians(70), 1, 0.05f, 2000);
            Matrix4f vp = new Matrix4f(projection).mul(view);
            matrix(program, "flw_viewProjection", vp);
            matrix(program, "flw_viewProjectionInverse", new Matrix4f(vp).invert());
            matrix(program, "flw_viewInverse", new Matrix4f(view).invert());
            matrix(program, "flw_view", view);
            glUniform3f(glGetUniformLocation(program, "flw_cameraPos"), 0, 0, cameraZ);
            glUniform3f(glGetUniformLocation(program, "flw_light0Direction"), 0, 1, 0);
            glUniform1i(glGetUniformLocation(program, "uSlot"), slot);
            HeadlessGl.samplerUnit(program, "_gemrender_volumes", 0);
            HeadlessGl.samplerUnit(program, "flw_diffuseTex", 1);
            HeadlessGl.samplerUnit(program, "_gemrender_sceneDepth", 2);
            glActiveTexture(GL_TEXTURE2);
            glBindTexture(GL_TEXTURE_2D, scene);
            float[] depth = new float[SIZE * SIZE];
            Arrays.fill(depth, sceneDistance);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_R32F, SIZE, SIZE, 0, GL_RED, GL_FLOAT, depth);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glBindVertexArray(vao);
            glDrawArrays(GL_TRIANGLES, 0, 6);
            assertThat(glGetError()).isEqualTo(GL_NO_ERROR);
        }

        float[] pixel(int attachment) { return pixelAt(attachment, SIZE / 2, SIZE / 2); }

        float[] pixelAt(int attachment, int x, int y) {
            glReadBuffer(GL_COLOR_ATTACHMENT0 + attachment);
            float[] pixel = new float[4];
            glReadPixels(x, y, 1, 1, GL_RGBA, GL_FLOAT, pixel);
            return pixel;
        }

        void preview(String name) throws Exception {
            float[][] layers = new float[2][SIZE * SIZE * 4];
            for (int i = 0; i < 2; i++) {
                glReadBuffer(GL_COLOR_ATTACHMENT0 + i);
                glReadPixels(0, 0, SIZE, SIZE, GL_RGBA, GL_FLOAT, layers[i]);
            }
            BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
            float[] sky = {0.006f, 0.01f, 0.02f};
            for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
                int at = (y * SIZE + x) * 4, rgb = 0;
                float transmission = (float) Math.exp(-layers[0][at + 3]);
                for (int c = 0; c < 3; c++) {
                    float light = layers[0][at + c] * (1 - transmission)
                            + (layers[1][at + c] + sky[c]) * transmission;
                    int color = Math.round((float) Math.pow(Math.min(Math.max(light, 0), 1), 1 / 2.2) * 255);
                    rgb = rgb << 8 | color;
                }
                image.setRGB(x, SIZE - y - 1, rgb);
            }
            var output = java.nio.file.Path.of("build", "fx-previews", name + ".png");
            java.nio.file.Files.createDirectories(output.getParent());
            ImageIO.write(image, "png", output.toFile());
        }

        @Override
        public void close() {
            glDeleteFramebuffers(fbo);
            glDeleteBuffers(buffer);
            glDeleteTextures(new int[]{records, noise, scene, outputs[0], outputs[1]});
            glDeleteVertexArrays(vao);
        }
    }
}
