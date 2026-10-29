package com.wf.gemrender.light;

import com.wf.gemrender.GemRender;
import com.wf.gemrender.Ids;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.lwjgl.opengl.GL11C.glGetInteger;
import static org.lwjgl.opengl.GL20C.*;
import static org.lwjgl.opengl.GL31C.GL_INVALID_INDEX;
import static org.lwjgl.opengl.GL31C.glGetUniformBlockIndex;
import static org.lwjgl.opengl.GL31C.glUniformBlockBinding;

/**
 * Source patches: before fog, {@code colour += albedo * gemrender_lights(p, n)}; the lit colour is never rebuilt as
 * albedo x lightmap (on Sable sub-levels that product loses the face shading); IR term ->
 * {@code _grl_irOut} ({@link #irLocation}, {@link IrTarget}). Albedo = lit colour / lightmap sample, the vanilla path fed
 * {@code max(lightmap, 1/255)} => a zeroed lightmap (darkness mods) still recovers albedo.
 * Anchor missing => throw: a changed upstream shader must not silently go unlit.
 */
public final class LightShaders {
    /** 26.1+ core shader ids (path only); earlier versions use the rendertype_* program names. */
    //? if >=26.1 {
    /*private static final Set<String> VANILLA = Set.of("core/terrain", "core/block", "core/entity", "core/item",
            "core/particle");
    *///?} else {
    private static final Set<String> VANILLA = Set.of("rendertype_solid", "rendertype_cutout_mipped",
            "rendertype_cutout", "rendertype_translucent", "rendertype_tripwire", "rendertype_entity_solid",
            "rendertype_entity_cutout", "rendertype_entity_cutout_no_cull", "rendertype_entity_cutout_no_cull_z_offset",
            "rendertype_entity_translucent", "rendertype_entity_translucent_cull", "rendertype_entity_smooth_cutout",
            "rendertype_entity_no_outline", "rendertype_entity_decal", "rendertype_item_entity_translucent_cull",
            "rendertype_armor_cutout_no_cull", "particle");
    //?}

    /** Block formats without a Normal attribute: facing from position derivatives, as on Sodium. */
    //? if >=26.1 {
    /*private static final Set<String> FACE_NORMAL = Set.of("core/terrain", "core/block");
    *///?} else {
    private static final Set<String> FACE_NORMAL = Set.of();
    //?}

    /**
     * Lightmap call head; whole call wrapped through its matching paren. Arguments vary: Veil reprint
     * {@code texelFetch(Sampler2, (UV2 / 16), 0)}, Sable {@code minecraft_sample_lightmap(Sampler2, ivec2((UV2 * ...)))}.
     */
//? if >=26.1 {
    /*private static final Pattern VANILLA_LIGHTMAP = Pattern.compile(
            "\\b(?:sample_lightmap|minecraft_sample_lightmap|texelFetch)\\(\\s*Sampler2\\s*,");
    private static final Pattern VANILLA_FOG = Pattern.compile(
            "fragColor = (?:linear_fog\\(color,|apply_fog\\(color,)");
*///?} else {
    private static final Pattern VANILLA_LIGHTMAP = Pattern.compile(
            "\\b(?:minecraft_sample_lightmap|texelFetch)\\(\\s*Sampler2\\s*,");
    private static final Pattern VANILLA_FOG = Pattern.compile("fragColor = linear_fog\\(color,");
//?}
    private static final Pattern MAIN = Pattern.compile("void\\s+main\\s*\\(\\s*\\)\\s*\\{");
    /** Veil dynamic buffers reprint it as {@code layout(location = 0) out vec4 fragColor;}. */
    private static final Pattern VANILLA_OUT = Pattern.compile(
            "(?m)^\\s*(?:layout\\s*\\(\\s*location\\s*=\\s*0\\s*\\)\\s*)?out\\s+vec4\\s+fragColor\\s*;");

    /** Sodium {@code texture(u_LightTex, uv)} (uv 0..1); Embeddium 0.3 {@code _sample_lightmap(u_LightTex, uv)} (uv 0..256). */
    private static final Pattern SODIUM_LIGHTMAP =
            Pattern.compile("\\b(?:_sample_lightmap|texture)\\(\\s*u_LightTex\\s*,");
    private static final Pattern SODIUM_POSITION = Pattern.compile(
            "vec3 position = _vert_position \\+ translation;");
    private static final Pattern SODIUM_FOG = Pattern.compile("fragColor = _linearFog\\((\\w+),");
    private static final ResourceLocation SODIUM_VERTEX = Ids.of("sodium",
            "blocks/block_layer_opaque.vsh");
    private static final ResourceLocation SODIUM_FRAGMENT = Ids.of("sodium",
            "blocks/block_layer_opaque.fsh");

    private static final Pattern FLYWHEEL_LIGHT = Pattern.compile("    vec4 lightColor = vec4\\(1\\.\\);");
    private static final ResourceLocation FLYWHEEL_COMMON = Ids.of("flywheel",
            "internal/common.frag");
    private static final Pattern FLYWHEEL_MAIN = Pattern.compile("void _flw_main\\(\\) \\{");
    private static final Pattern FLYWHEEL_FOG = Pattern.compile("    color = flw_fogFilter\\(color\\);");
    private static final Pattern FLYWHEEL_OUT = Pattern.compile("(?m)^out vec4 _flw_outputColor;");

    private static final String VOXY_VERTEX = "voxy:lod/gl46/quads3.vert";
    private static final String VOXY_FRAGMENT = "voxy:lod/gl46/quads.frag";
    private static final Pattern VOXY_TINTING = Pattern.compile("vec4 tinting = getLighting\\(lighting\\);");
    private static final Pattern VOXY_ATTRIBUTES = Pattern.compile("attributes\\.z = addin\\|\\(face<<8\\);");
    private static final Pattern VOXY_CORNER_FN = Pattern.compile("vec4 getQuadCornerPos\\(in QuadData quad, uint cornerId\\) \\{");
    private static final Pattern VOXY_CORNER = Pattern.compile("vec4 pos = MVP \\* vec4\\(point, 1\\.0f\\);");
    private static final Pattern VOXY_POSITION = Pattern.compile("\\(getQuadCornerPos\\(quad, cornerId\\)\\);");
    private static final Pattern VOXY_FIRST_UNIFORM = Pattern.compile("(?m)^layout\\(binding = 0\\) uniform sampler2D blockModelAtlas;");
    private static final Pattern VOXY_OUT = Pattern.compile("(?m)^layout\\(location = 0\\) out vec4 outColour;");
    private static final Pattern VOXY_COLOUR = Pattern.compile("(?m)^    colour = computeColour\\(texPos, colour\\);");
    /** Location 2 free: Voxy uses 0, 1 (uv), 7 (debug). */
    private static final String VOXY_POS_OUT = """
            #ifndef PATCHED_SHADER
            layout(location = 2) out vec3 _grl_pos;
            #endif
            """;
    private static final String VOXY_ADD = """
            $0
                uint _grl_id = interData.w >> 16;
                float _grl_sky = float(_grl_id & 15u) / 15.0;
                uint _grl_face = getFace();
                const int _grl_axes[3] = int[3](1, 2, 0);
                vec3 _grl_n = vec3(0.0);
                _grl_n[_grl_axes[_grl_face >> 1]] = (_grl_face & 1u) == 1u ? 1.0 : -1.0;
                vec3 _grl_albedo = colour.rgb;
                colour.rgb = _grl_albedo * (texture(_grl_voxyLightmap, getLightmapUv(_grl_id)).rgb
                        + gemrender_farLights(_grl_pos, _grl_n) + gemrender_mergedNight(_grl_sky));
                #ifdef TRANSLUCENT
                _grl_irOut = vec4(_grl_albedo * (_grl_irSum + gemrender_nightSky(_grl_sky)), colour.a);
                #else
                _grl_irOut = vec4(_grl_albedo * (_grl_irSum + gemrender_nightSky(_grl_sky)), 1.0);
                #endif
            """;

    private static final String VERTEX_DECLARATIONS = """
            out vec3 _grl_pos;
            out vec3 _grl_normal;
            out vec4 _grl_lmColor;
            out float _grl_sky;
            vec4 _grl_lm(vec4 c, float sky) {
                _grl_lmColor = c;
                _grl_sky = sky;
                return max(c, vec4(1.0 / 255.0));
            }
            """;

    /** %d IR output location. */
    private static final String FRAGMENT_INPUTS = """
            in vec3 _grl_pos;
            in vec3 _grl_normal;
            in vec4 _grl_lmColor;
            in float _grl_sky;
            layout(location = %d) out vec4 _grl_irOut;
            """;

    /** GLSL 150 (vanilla): explicit output locations. */
    private static final String EXPLICIT_LOCATION = "#extension GL_ARB_explicit_attrib_location : require\n";

    private static final String PREPARE = "$0\n    gemrender_lightPrepare(_grl_pos);\n    _grl_irOut = vec4(0.0);";
    /**
     * Sodium: prepare spliced before fog, not at {@code main} head. Veil {@code SodiumShaderPreProcessor} inserts its
     * albedo output at main body index 1, reading {@code lodBias} declared by statement 0.
     */
    private static final String SODIUM_PREPARE = "gemrender_lightPrepare(_grl_pos);\n    _grl_irOut = vec4(0.0);\n    ";

    /** %1$s colour variable, %2$s light call. */
    private static final String ADD = """
            vec3 _grl_albedo = %1$s.rgb / max(_grl_lmColor.rgb, vec3(1.0 / 255.0));
                %1$s.rgb += _grl_albedo * (%2$s + gemrender_mergedNight(_grl_sky));
                _grl_irOut = vec4(_grl_albedo * (_grl_irSum + gemrender_nightSky(_grl_sky)), %1$s.a);
                """;

    private static String include;
    private static volatile MethodHandle veilRenderer;
    private static MethodHandle veilMask;
    /** Shaders whose vertex stage a resource pack replaced beyond patching; their fragment stage is left alone too. */
    private static final Set<String> UNPATCHED = new HashSet<>();

    private LightShaders() {
    }

    /** GLSL text spliced after {@code #version}; defines from the Java constants. */
    public static synchronized String include() {
        if (include == null) {
            try (InputStream in = LightShaders.class.getResourceAsStream(
                    "/assets/" + GemRender.MOD_ID + "/shaders/include/lights.glsl")) {
                include = "#define GEMRENDER_LIGHT_MAX " + Lights.MAX + "\n"
                        + "#define GEMRENDER_LIGHT_GRID " + LightGrid.GRID + "\n"
                        + "#define GEMRENDER_LIGHT_COOKIE_SIZE " + LightCookies.SIZE + "\n"
                        + "#define GEMRENDER_LIGHT_SHADOW_RES " + LightShadows.RES + "\n"
                        + "#define GEMRENDER_LIGHT_SHADOW_TILES " + LightShadows.TILES + "\n"
                        + "#define GEMRENDER_LIGHT_SHADOW_MAX " + LightShadows.MAX + "\n"
                        + "#define GEMRENDER_LIGHT_OCCUPANCY " + LightOccupancy.SIZE + "\n"
                        + new String(in.readAllBytes(), StandardCharsets.UTF_8) + "\n";
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return include;
    }

    public static String vanilla(String name, boolean vertex, String source) {
        return vanilla(name, vertex, source, irLocation(veilBuffers()));
    }

    /**
     * A resource pack's own copy of a vanilla shader may not fit the anchors. That shader then goes unlit by GemRender
     * rather than failing the reload, which vanilla retries forever when the pack is a mod's. The vertex stage
     * compiles first, so a skipped vertex skips its fragment, whose inputs it would not write.
     *
     * @param irLocation {@link #irLocation} of the Veil buffer mask this program is compiled for
     */
    public static String vanilla(String name, boolean vertex, String source, int irLocation) {
        if (!VANILLA.contains(name)) {
            return source;
        }
        synchronized (UNPATCHED) {
            if (vertex) {
                UNPATCHED.remove(name);
            } else if (UNPATCHED.contains(name)) {
                return source;
            }
        }
        try {
            return patchVanilla(name, vertex, source, irLocation);
        } catch (IllegalStateException e) {
            GemRender.LOGGER.warn("Lights skip {} ({}): {}", name, vertex ? "vertex" : "fragment", e.getMessage());
            if (vertex) {
                synchronized (UNPATCHED) {
                    UNPATCHED.add(name);
                }
            }
            return source;
        }
    }

    private static String patchVanilla(String name, boolean vertex, String source, int irLocation) {
        if (vertex) {
//? if >=26.1 {
            /*String position = source.contains("ChunkPosition")
                    ? "Position + (ChunkPosition - CameraBlockPos) + CameraOffset"
                    : source.contains("uniform vec3 ChunkOffset;") ? "Position + ChunkOffset"
                    : source.contains("Position + ModelOffset") ? "Position + ModelOffset"
                    : "Position";
*///?} else {
            String position = source.contains("uniform vec3 ChunkOffset;") ? "Position + ChunkOffset" : "Position";
//?}
            // 1.20.1 entity shaders receive view-rotated vertices; IViewRotMat turns them back to world orientation.
            String unrotate = source.contains("uniform mat3 IViewRotMat;") ? "IViewRotMat * " : "";
            position = unrotate + "(" + position + ")";
            String normal = source.contains("in vec3 Normal;") ? unrotate + "Normal" : "vec3(0.0)";
            String patched = wrapLightmap(name, source, VANILLA_LIGHTMAP, LightShaders::vanillaSky);
            patched = replaceOne(name, patched, MAIN, "void _grl_main() {");
            return afterVersion(name, patched, VERTEX_DECLARATIONS) + "\nvoid main() {\n    _grl_lmColor = vec4(1.0);\n    _grl_sky = 0.0;\n"
                    + "    _grl_main();\n    _grl_pos = " + position + ";\n    _grl_normal = " + normal + ";\n}\n";
        }
        String patched = replaceOne(name, source, MAIN, PREPARE);
        patched = replaceOne(name, patched, VANILLA_OUT, "layout(location = 0) out vec4 fragColor;");
        patched = replaceOne(name, patched, VANILLA_FOG,
                ADD.formatted("color", "gemrender_lights(_grl_pos, " + (FACE_NORMAL.contains(name)
                        ? "gemrender_lightFaceNormal(_grl_pos)" : "gemrender_lightNormal(_grl_normal)") + ")") + "$0");
        return afterVersion(name, patched, EXPLICIT_LOCATION + include() + fragmentInputs(name, patched, irLocation));
    }

    public static String sodium(ResourceLocation id, String source) {
        return sodium(id, source, irLocation(veilBuffers()));
    }

    /** @param irLocation {@link #irLocation} of the Veil buffer mask Veil applies to this compile */
    public static String sodium(ResourceLocation id, String source, int irLocation) {
        String name = id.toString();
        if (id.equals(SODIUM_VERTEX)) {
            String patched = wrapLightmap(name, source, SODIUM_LIGHTMAP,
                    (head, coord) -> head.startsWith("_sample_lightmap")
                            ? "clamp((float((" + coord + ").y) / 16.0 - 0.5) / 15.0, 0.0, 1.0)"
                            : "clamp(((" + coord + ").y * 16.0 - 0.5) / 15.0, 0.0, 1.0)");
            patched = replaceOne(name, patched, SODIUM_POSITION, "$0\n    _grl_pos = position;");
            return afterVersion(name, patched, VERTEX_DECLARATIONS);
        }
        if (id.equals(SODIUM_FRAGMENT)) {
            String patched = replaceOne(name, source, VANILLA_OUT, "layout(location = 0) out vec4 fragColor;");
            patched = replaceOne(name, patched, SODIUM_FOG, SODIUM_PREPARE
                    + ADD.formatted("$1", "gemrender_lights(_grl_pos, gemrender_lightFaceNormal(_grl_pos))") + "$0");
            return afterVersion(name, patched, include() + fragmentInputs(name, patched, irLocation));
        }
        return source;
    }

    /**
     * Voxy LOD terrain ({@code ShaderLoader.parse} output, imports inlined, defines not yet injected): the vertex
     * stage packs the light id ({@code block << 4 | sky}) into {@code interData.w} bits 16-23 instead of baking an
     * 8-bit lit tint, so albedo stays exact at lightmap 0. Dynamic lights: far list only. IR -> location 1 ->
     * Voxy aux target ({@code voxy.auxColour}). Lightmap = Voxy's unit 1. {@code PATCHED_SHADER} (Iris) untouched.
     */
    public static String voxy(String id, String source) {
        if (id.equals(VOXY_VERTEX)) {
            String patched = replaceOne(id, source, VOXY_TINTING, "vec4 tinting = vec4(1.0);");
            patched = replaceOne(id, patched, VOXY_ATTRIBUTES, "attributes.z = addin|(face<<8)|(lighting<<16);");
            patched = replaceOne(id, patched, VOXY_CORNER_FN, "vec3 _grl_corner;\n" + VOXY_POS_OUT + "$0");
            patched = replaceOne(id, patched, VOXY_CORNER, "_grl_corner = point;\n    $0");
            return replaceOne(id, patched, VOXY_POSITION,
                    "$0\n#ifndef PATCHED_SHADER\n    _grl_pos = _grl_corner - cameraSubPos;\n#endif");
        }
        if (id.equals(VOXY_FRAGMENT)) {
            String patched = replaceOne(id, source, VOXY_OUT, "$0\nlayout(location = 1) out vec4 _grl_irOut;");
            patched = replaceOne(id, patched, MAIN, "$0\n#ifndef PATCHED_SHADER\n    gemrender_lightPrepare(_grl_pos);\n#endif");
            patched = replaceOne(id, patched, VOXY_COLOUR, VOXY_ADD);
            // Before the first declaration: #extension lines may follow #version.
            return replaceOne(id, patched, VOXY_FIRST_UNIFORM, Matcher.quoteReplacement("#ifndef PATCHED_SHADER\n"
                    + include() + "layout(binding = 1) uniform sampler2D _grl_voxyLightmap;\n"
                    + "layout(location = 2) in vec3 _grl_pos;\n#endif\n") + "$0");
        }
        return source;
    }

    /**
     * Flywheel concatenates includes; no #version in the file (330+). Light only where the lightmap applies.
     * Gap: OIT passes (own targets, locations 0-3 taken) => no IR output.
     */
    public static String flywheel(ResourceLocation id, String source) {
        if (!id.equals(FLYWHEEL_COMMON)) {
            return source;
        }
        String name = id.toString();
        String patched = replaceOne(name, source, FLYWHEEL_MAIN, "$0\n    vec3 _grl_pos = flw_vertexPos.xyz - flw_cameraPos;"
                + "\n    gemrender_lightPrepare(_grl_pos);");
        patched = replaceOne(name, patched, FLYWHEEL_LIGHT, "    vec3 _grl_albedo = color.rgb;\n$0");
        patched = replaceOne(name, patched, FLYWHEEL_OUT,
                "layout(location = 0) out vec4 _flw_outputColor;\nlayout(location = 1) out vec4 _grl_irOut;");
        patched = replaceOne(name, patched, FLYWHEEL_FOG, "#ifndef _FLW_OIT\n    _grl_irOut = vec4(0.0);\n#endif\n"
                + "    if (flw_material.useLight) {\n"
                + "        color.rgb += _grl_albedo * (gemrender_lights(_grl_pos, gemrender_lightNormal(flw_vertexNormal))\n"
                + "                + gemrender_mergedNight(flw_fragLight.y));\n"
                + "#ifndef _FLW_OIT\n        _grl_irOut = vec4(_grl_albedo * (_grl_irSum + gemrender_nightSky(flw_fragLight.y)), color.a);\n#endif\n"
                + "    }\n$0");
        return include() + patched;
    }


    /** After link: block -> {@link LightFrame#UBO_BINDING}, samplers -> units. No block => untouched. */
    public static void bindProgram(int program) {
        //? if >=26.1 {
        /*if (program <= 0) {
            return;
        }
        *///?}
        int block = glGetUniformBlockIndex(program, "GemRenderLights");
        if (block == GL_INVALID_INDEX) {
            return;
        }
        LightFrame.getInstance().ensureBuffers();
        glUniformBlockBinding(program, block, LightFrame.UBO_BINDING);
        int previous = glGetInteger(GL_CURRENT_PROGRAM);
        glUseProgram(program);
        glUniform1i(glGetUniformLocation(program, "_grl_cookies"), LightCookies.UNIT);
        glUniform1i(glGetUniformLocation(program, "_grl_grid"), LightGrid.UNIT);
        glUniform1i(glGetUniformLocation(program, "_grl_shadows"), LightShadows.UNIT);
        // A reload deletes the programs it replaces; one still current dies once unbound here.
        glUseProgram(glIsProgram(previous) ? previous : 0);
    }

    static boolean standDown() {
        //? if iris {
        return com.wf.gemrender.iris.ShaderPacks.inUse();
        //?} else {
        /*return false;
        *///?}
    }

    /** First fragment output after Veil's dynamic buffers ({@code layout(location = 1 + i)} per active type). */
    public static int irLocation(int veilBuffers) {
        return 1 + Integer.bitCount(veilBuffers);
    }

    /** Veil {@code DynamicBufferManager} active mask; 0 without Veil, before Veil init, or on a Veil without dynamic buffers. */
    static int veilBuffers() {
        if (veilRenderer == null) {
            resolveVeil();
        }
        try {
            Object renderer = veilRenderer.invoke();
            return renderer == null ? 0 : (int) veilMask.invoke(renderer);
        } catch (Throwable t) {
            throw new IllegalStateException("GemRender lights: Veil buffer mask", t);
        }
    }

    private static synchronized void resolveVeil() {
        if (veilRenderer != null) {
            return;
        }
        MethodHandle renderer = MethodHandles.constant(Object.class, null);
        MethodHandle mask = null;
        try {
            Class<?> system = Class.forName("foundry.veil.api.client.render.VeilRenderSystem");
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            Method rendererGetter = system.getMethod("renderer");
            Method managerGetter = rendererGetter.getReturnType().getMethod("getDynamicBufferManger");
            MethodHandle manager = lookup.unreflect(managerGetter);
            mask = MethodHandles.filterReturnValue(manager,
                    lookup.unreflect(managerGetter.getReturnType().getMethod("getActiveBuffers")));
            renderer = lookup.unreflect(rendererGetter);
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
        veilMask = mask;
        veilRenderer = renderer;
    }

    private static String fragmentInputs(String name, String source, int irLocation) {
        if (Pattern.compile("layout\\s*\\(\\s*location\\s*=\\s*" + irLocation + "\\s*\\)\\s*out\\b").matcher(source).find()) {
            throw new IllegalStateException("GemRender lights: " + name + " already has an output at IR location " + irLocation);
        }
        return FRAGMENT_INPUTS.formatted(irLocation);
    }

    private static String afterVersion(String name, String source, String text) {
        Matcher version = Pattern.compile("(?m)^#version.*$").matcher(source);
        if (!version.find()) {
            throw new IllegalStateException("GemRender lights: no #version in " + name);
        }
        return source.substring(0, version.end()) + "\n" + text + source.substring(version.end());
    }

    private static String replaceOne(String name, String source, Pattern anchor, String replacement) {
        Matcher matcher = anchor.matcher(source);
        int found = 0;
        while (matcher.find()) {
            found++;
        }
        if (found != 1) {
            throw new IllegalStateException("GemRender lights: " + name + " has " + found + " of /" + anchor + "/");
        }
        return anchor.matcher(source).replaceFirst(replacement);
    }

    /** Sky level 0..1 from a vanilla lightmap call's coordinate: packed 0..240, or texel 0..15. */
    private static String vanillaSky(String head, String coord) {
        return head.startsWith("texelFetch") ? "clamp(float((" + coord + ").y) / 15.0, 0.0, 1.0)"
                : "clamp(float((" + coord + ").y) / 240.0, 0.0, 1.0)";
    }

    /** Every call opened by {@code head} -> {@code _grl_lm(call, sky(head, coordinate argument))}. */
    private static String wrapLightmap(String name, String source, Pattern head,
                                       java.util.function.BiFunction<String, String, String> sky) {
        Matcher matcher = head.matcher(source);
        StringBuilder out = new StringBuilder(source.length() + 64);
        int copied = 0;
        while (matcher.find()) {
            int end = matcher.end();
            for (int depth = 1; depth > 0; end++) {
                if (end == source.length()) {
                    throw new IllegalStateException("GemRender lights: " + name + " lightmap call unclosed at " + matcher.start());
                }
                char c = source.charAt(end);
                depth += c == '(' ? 1 : c == ')' ? -1 : 0;
            }
            String coord = firstArgument(source, matcher.end(), end - 1);
            out.append(source, copied, matcher.start()).append("_grl_lm(").append(source, matcher.start(), end)
                    .append(", ").append(sky.apply(matcher.group(), coord)).append(')');
            copied = end;
        }
        if (copied == 0) {
            throw new IllegalStateException("GemRender lights: " + name + " has no /" + head + "/; lightmap lines: "
                    + source.lines().filter(l -> l.contains("Sampler2") || l.contains("UV2") || l.contains("lightmap"))
                    .map(String::strip).toList());
        }
        return out.append(source, copied, source.length()).toString();
    }

    /** {@code source[from, to)} up to its first top-level comma. */
    private static String firstArgument(String source, int from, int to) {
        int depth = 0;
        for (int i = from; i < to; i++) {
            char c = source.charAt(i);
            depth += c == '(' ? 1 : c == ')' ? -1 : 0;
            if (c == ',' && depth == 0) {
                return source.substring(from, i).strip();
            }
        }
        return source.substring(from, to).strip();
    }
}
