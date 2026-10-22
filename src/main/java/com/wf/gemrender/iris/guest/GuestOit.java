package com.wf.gemrender.iris.guest;

import com.wf.gemrender.render.GlState;
import com.mojang.blaze3d.platform.GlStateManager;
import com.wf.gemrender.render.TextureUnits;
import dev.engine_room.flywheel.backend.NoiseTextures;
import net.irisshaders.iris.gl.sampler.SamplerLimits;
import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL11C.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11C.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11C.glDrawArrays;
import static org.lwjgl.opengl.GL20C.*;
import static org.lwjgl.opengl.GL30C.GL_TEXTURE_2D_ARRAY;
import static org.lwjgl.opengl.GL30C.glBindVertexArray;
import static org.lwjgl.opengl.GL30C.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30C.glGenVertexArrays;

/**
 * Wavelet OIT producers (CrankShaft {@code GuestOitCodegen}), main view only: forward Z => device depth stored as
 * {@code 1 - z} so the MAX blend keeps the nearest. Linear depth = {@code 1 / gl_FragCoord.w}: no varying, so
 * geometry stages pass through.
 */
final class GuestOit {
    private static final String VERSION = "#version 330 core\n";
    private static final String FULLSCREEN = VERSION + """
            void main() {
                vec2 corner = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
                gl_Position = vec4(corner * 2.0 - 1.0, 0.0, 1.0);
            }
            """;
    private static final String DEPTH = VERSION + """
            uniform sampler2D _gr_oitDepthRange;
            void main() {
                float stored = texelFetch(_gr_oitDepthRange, ivec2(gl_FragCoord.xy), 0).b;
                if (stored <= 0.0) {
                    discard;
                }
                gl_FragDepth = 1.0 - stored;
            }
            """;

    @Nullable
    private static Integer maxUnits;
    private static final Map<ForwardOit, Resources> RESOURCES = new IdentityHashMap<>();
    private static int depthProgram;
    private static int emptyVao;

    private GuestOit() {
    }

    /** Producer samplers sit on fixed units Iris must leave alone: the last coefficient set's must exist. */
    static boolean supports(ForwardOit oit) {
        if (maxUnits == null) {
            maxUnits = SamplerLimits.get()
                    .getMaxTextureUnits();
        }
        return TextureUnits.GUEST_OIT_COEFFICIENTS + oit.sets() <= maxUnits;
    }

    private record Resources(GuestOitTargets targets, int composite) {
    }

    static void release() {
        RESOURCES.values()
                .forEach(resources -> {
                    resources.targets()
                            .delete();
                    glDeleteProgram(resources.composite());
                });
        RESOURCES.clear();
        if (depthProgram != 0) {
            glDeleteProgram(depthProgram);
            glDeleteVertexArrays(emptyVao);
            depthProgram = 0;
            emptyVao = 0;
        }
    }

    static GuestOitTargets targets(ForwardOit oit) {
        return resources(oit).targets();
    }

    private static Resources resources(ForwardOit oit) {
        return RESOURCES.computeIfAbsent(oit, key -> new Resources(new GuestOitTargets(key.ranks()),
                link("gemrender_guest_oit_composite", FULLSCREEN, compositeFragment(key))));
    }

    /** Producer textures for {@code pass}; after {@code shader.apply()}. */
    static void bindProducerTextures(ForwardOit oit, OitPass pass) {
        if (pass == OitPass.DEPTH_RANGE) {
            return;
        }
        GuestOitTargets targets = targets(oit);
        bind(TextureUnits.GUEST_OIT_DEPTH_RANGE, GL_TEXTURE_2D, targets.depthRange());
        bind(TextureUnits.GUEST_OIT_BLUE_NOISE, GL_TEXTURE_2D, NoiseTextures.BLUE_NOISE.getId());
        if (pass == OitPass.EVALUATE) {
            for (int set = 0; set < oit.sets(); set++) {
                bind(TextureUnits.GUEST_OIT_COEFFICIENTS + set, GL_TEXTURE_2D_ARRAY, targets.coefficients(set));
            }
        }
    }

    /** Into the bound framebuffer (the pack's translucent targets): source-over, then nearest depth. */
    static void composite(ForwardOit oit) {
        Resources resources = resources(oit);
        GuestOitTargets targets = resources.targets();
        if (depthProgram == 0) {
            depthProgram = link("gemrender_guest_oit_depth", FULLSCREEN, DEPTH);
            emptyVao = glGenVertexArrays();
        }

        int unit = 0;
        bind(unit++, GL_TEXTURE_2D, targets.depthRange());
        for (int set = 0; set < oit.sets(); set++) {
            bind(unit++, GL_TEXTURE_2D_ARRAY, targets.coefficients(set));
        }
        for (int slot = 0; slot < oit.drawBuffers().length; slot++) {
            bind(unit++, GL_TEXTURE_2D, targets.accumulate(slot));
        }

        glBindVertexArray(emptyVao);
        GlStateManager._disableDepthTest();
        GlStateManager._depthMask(false);
        GlStateManager._disableCull();
        GlState.enableBlend();
        GlStateManager._blendEquation(GL_FUNC_ADD);
        GlStateManager._blendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager._glUseProgram(resources.composite());
        glDrawArrays(GL_TRIANGLES, 0, 3);

        GlState.disableBlend();
        GlStateManager._colorMask(false, false, false, false);
        GlStateManager._enableDepthTest();
        GlStateManager._depthFunc(GL_LEQUAL);
        GlStateManager._depthMask(true);
        GlStateManager._glUseProgram(depthProgram);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        GlStateManager._colorMask(true, true, true, true);

        glBindVertexArray(0);
        GlStateManager._glUseProgram(0);
        for (int i = 0; i < unit; i++) {
            bind(i, i - 1 < oit.sets() && i > 0 ? GL_TEXTURE_2D_ARRAY : GL_TEXTURE_2D, 0);
        }
    }

    static void unbindProducerTextures(ForwardOit oit) {
        bind(TextureUnits.GUEST_OIT_DEPTH_RANGE, GL_TEXTURE_2D, 0);
        bind(TextureUnits.GUEST_OIT_BLUE_NOISE, GL_TEXTURE_2D, 0);
        for (int set = 0; set < oit.sets(); set++) {
            bind(TextureUnits.GUEST_OIT_COEFFICIENTS + set, GL_TEXTURE_2D_ARRAY, 0);
        }
    }

    private static void bind(int unit, int target, int texture) {
        GlStateManager._activeTexture(GL_TEXTURE0 + unit);
        if (target == GL_TEXTURE_2D) {
            GlStateManager._bindTexture(texture);
        } else {
            org.lwjgl.opengl.GL11C.glBindTexture(target, texture);
        }
        GlStateManager._activeTexture(GL_TEXTURE0);
    }

    /** Appended after the demoted pack fragment and {@code oit.glsl}. */
    static String producer(OitPass pass, ForwardOit oit, Map<Integer, String> outputs) {
        StringBuilder out = new StringBuilder();
        switch (pass) {
            case DEPTH_RANGE -> out.append("""
                    layout(location = 0) out vec4 _gr_oitDepthRangeOut;
                    void main() {
                        _gr_irisMain();
                        float linearDepth = 1.0 / gl_FragCoord.w;
                        _gr_oitDepthRangeOut = vec4(-linearDepth + 1e-5, linearDepth + 1e-2, 1.0 - gl_FragCoord.z, 0.0);
                    }
                    """);
            case COEFFICIENTS -> coefficients(out, oit, outputs);
            case EVALUATE -> evaluate(out, oit, outputs);
        }
        return out.toString();
    }

    private static void normalizedDepth(StringBuilder out) {
        out.append("""
                    _gr_irisMain();
                    float linearDepth = 1.0 / gl_FragCoord.w;
                    vec2 depthRange = texelFetch(_gr_oitDepthRange, ivec2(gl_FragCoord.xy), 0).rg;
                    float depth = (linearDepth + depthRange.x) / (depthRange.x + depthRange.y);
                    float adjustment = _gr_oitTentedBlueNoise(depth) * _GR_OIT_NOISE;
                """);
    }

    /** Pack's colour for draw buffer {@code slot}, normalised target => clamped. */
    private static String source(Map<Integer, String> outputs, int slot) {
        String source = outputs.get(slot);
        if (source == null) {
            throw new IllegalStateException("OIT draw buffer slot " + slot + " is never written by the pack");
        }
        return "clamp(" + source + ", 0.0, 1.0)";
    }

    private static void coefficients(StringBuilder out, ForwardOit oit, Map<Integer, String> outputs) {
        int attachment = 0;
        for (int rank : oit.ranks()) {
            for (int layer = 0; layer < ForwardOit.layers(rank); layer++) {
                out.append("layout(location = ")
                        .append(attachment)
                        .append(") out vec4 _gr_oitCoefficientsOut")
                        .append(attachment++)
                        .append(";\n");
            }
        }
        out.append("void main() {\n");
        normalizedDepth(out);
        attachment = 0;
        for (int set = 0; set < oit.sets(); set++) {
            out.append("    {\n        float transmittance = 1.0 - ")
                    .append(source(outputs, set))
                    .append(".a;\n        float d = transmittance > 1e-5 ? depth - adjustment : depth;\n")
                    .append("        vec4[4] c = vec4[4](vec4(0.0), vec4(0.0), vec4(0.0), vec4(0.0));\n")
                    .append("        _gr_oitAddTransmittance(c, transmittance, d, ")
                    .append(oit.ranks()[set])
                    .append(");\n");
            for (int layer = 0; layer < ForwardOit.layers(oit.ranks()[set]); layer++) {
                out.append("        _gr_oitCoefficientsOut")
                        .append(attachment++)
                        .append(" = c[")
                        .append(layer)
                        .append("];\n");
            }
            out.append("    }\n");
        }
        out.append("}\n");
    }

    private static void evaluate(StringBuilder out, ForwardOit oit, Map<Integer, String> outputs) {
        for (int set = 0; set < oit.sets(); set++) {
            out.append("uniform sampler2DArray _gr_oitCoefficients")
                    .append(set)
                    .append(";\nlayout(location = ")
                    .append(set)
                    .append(") out vec4 _gr_oitAccumulateOut")
                    .append(set)
                    .append(";\n");
        }
        out.append("void main() {\n");
        normalizedDepth(out);
        for (int slot = 0; slot < oit.sets(); slot++) {
            out.append("    {\n        vec4 v = ")
                    .append(source(outputs, slot))
                    .append(";\n        float transmittance = 1.0 - v.a;\n")
                    .append("        float d = transmittance > 1e-5 ? depth - adjustment : depth;\n")
                    .append(oit.premultiplied()[slot] ? "" : "        v.rgb *= v.a;\n")
                    .append("        v *= _gr_oitSignalCorrectedTransmittance(_gr_oitCoefficients")
                    .append(slot)
                    .append(", d, transmittance, ")
                    .append(oit.ranks()[slot])
                    .append(");\n        _gr_oitAccumulateOut")
                    .append(slot)
                    .append(" = v;\n    }\n");
        }
        out.append("}\n");
    }

    /**
     * Diverges from CrankShaft: every target composites {@code (rgb / a, 1 - T)} under SRC_ALPHA, premultiplied
     * included. CrankShaft scales premultiplied rgb by {@code 1 - T} as well, which the blend then applies again.
     */
    private static String compositeFragment(ForwardOit oit) {
        StringBuilder out = new StringBuilder(VERSION).append(GuestPrograms.oitLibrary());
        for (int set = 0; set < oit.sets(); set++) {
            out.append("uniform sampler2DArray _gr_oitCoefficients")
                    .append(set)
                    .append(";\n");
        }
        for (int slot = 0; slot < oit.sets(); slot++) {
            out.append("uniform sampler2D _gr_oitAccumulate")
                    .append(slot)
                    .append(";\nlayout(location = ")
                    .append(slot)
                    .append(") out vec4 _gr_frag")
                    .append(slot)
                    .append(";\n");
        }
        out.append("void main() {\n")
                .append("    vec4 range = texelFetch(_gr_oitDepthRange, ivec2(gl_FragCoord.xy), 0);\n")
                .append("    if (range.b <= 0.0) {\n        discard;\n    }\n");
        for (int slot = 0; slot < oit.sets(); slot++) {
            out.append("    float total")
                    .append(slot)
                    .append(" = _gr_oitTotalTransmittance(_gr_oitCoefficients")
                    .append(slot)
                    .append(", ")
                    .append(oit.ranks()[slot])
                    .append(");\n    vec4 texel")
                    .append(slot)
                    .append(" = texelFetch(_gr_oitAccumulate")
                    .append(slot)
                    .append(", ivec2(gl_FragCoord.xy), 0);\n    _gr_frag")
                    .append(slot)
                    .append(" = texel")
                    .append(slot)
                    .append(".a >= 1e-5 ? vec4(texel")
                    .append(slot)
                    .append(".rgb / texel")
                    .append(slot)
                    .append(".a, 1.0 - total")
                    .append(slot)
                    .append(") : vec4(0.0);\n");
        }
        return out.append("}\n")
                .toString();
    }

    /** Composite samplers: depth range 0, coefficient sets 1.., accumulators after. */
    private static int link(String name, String vertex, String fragment) {
        int program = glCreateProgram();
        int vertexShader = compile(name + ".vert", GL_VERTEX_SHADER, vertex);
        int fragmentShader = compile(name + ".frag", GL_FRAGMENT_SHADER, fragment);
        glAttachShader(program, vertexShader);
        glAttachShader(program, fragmentShader);
        glLinkProgram(program);
        glDeleteShader(vertexShader);
        glDeleteShader(fragmentShader);
        if (glGetProgrami(program, GL_LINK_STATUS) == 0) {
            throw new IllegalStateException("Could not link " + name + ": " + glGetProgramInfoLog(program));
        }

        GlStateManager._glUseProgram(program);
        int unit = 0;
        glUniform1i(glGetUniformLocation(program, "_gr_oitDepthRange"), unit++);
        for (int set = 0; glGetUniformLocation(program, "_gr_oitCoefficients" + set) >= 0; set++) {
            glUniform1i(glGetUniformLocation(program, "_gr_oitCoefficients" + set), unit++);
        }
        for (int slot = 0; glGetUniformLocation(program, "_gr_oitAccumulate" + slot) >= 0; slot++) {
            glUniform1i(glGetUniformLocation(program, "_gr_oitAccumulate" + slot), unit++);
        }
        GlStateManager._glUseProgram(0);
        return program;
    }

    private static int compile(String name, int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == 0) {
            throw new IllegalStateException("Could not compile " + name + ": " + glGetShaderInfoLog(shader));
        }
        return shader;
    }
}
