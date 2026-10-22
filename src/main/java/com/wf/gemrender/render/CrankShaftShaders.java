package com.wf.gemrender.render;

//? if >=26.2 {
/*import com.wf.gemrender.GemRender;
import net.minecraft.resources.ResourceLocation;

import java.util.regex.Pattern;

/^*
 * CrankShaft 26.2 dropped Flywheel 1.0's shader API that instance and material shaders (GemRender's, WfLib's) read:
 * {@code crankshaft/vertex.glsl} / {@code fragment.glsl} prepended to every non-CrankShaft {@code instance/} /
 * {@code material/} {@code .vert} / {@code .frag}, plus the few rewrites a define cannot express. Applied as each
 * source is parsed.
 ^/
public final class CrankShaftShaders {
    private static final String MARK = "// gemrender: CrankShaft 26.2 shim\n";
    private static final String VERTEX_SHIM = "#include \"gemrender:crankshaft/vertex.glsl\"\n";
    private static final String FRAGMENT_SHIM = "#include \"gemrender:crankshaft/fragment.glsl\"\n";
    private static final Pattern DIFFUSE_TEX = Pattern.compile("\\btexture\\(\\s*flw_diffuseTex\\s*,\\s*");
    /^* Overlay is applied after the material from the vertex stage's overlayCoord. ^/
    private static final Pattern NO_OVERLAY = Pattern.compile(
            "\\bflw_fragOverlay\\s*=\\s*ivec2\\(\\s*0\\s*,\\s*10\\s*\\)\\s*;");
    /^* Reverse-Z (Projection swaps near/far): near plane at NDC z = +1 under both clip-control modes. ^/
    private static final String VOLUME = "instance/volume.vert";
    private static final String VOLUME_NEAR = "vec4(ndc, -1.0 + 1e-4, 1.0)";
    private static final String VOLUME_NEAR_26 = "vec4(ndc, 1.0 - 1e-4, 1.0)";
    private static final String INSTANCING_MAIN = "renderpass/instancing_main.vert";
    private static final String SELECTOR_BASE_VERTEX = """

            #ifdef _FLW_INSTANCING_SELECTOR
            int gemrender_baseVertex() {
                return _flw_instanceSelector.z;
            }
            #endif
            """;
    private static final String INDIRECT_FRAGMENT = "renderpass/flw_indirect.frag";
    private static final String OIT_FRAGMENT = "renderpass/flw_oit.frag";
    private static final String DIFFUSE = """

            #ifndef _FLW_DEPTH_RANGE
            vec4 gemrender_diffuse(vec2 uv) {
                return texture(Sampler0, uv);
            }
            #endif
            """;

    private CrankShaftShaders() {
    }

    /^* Same instance when untouched; patched output is never patched again. ^/
    public static String patch(ResourceLocation id, String source) {
        if (source.startsWith(MARK)) {
            return source;
        }
        String path = id.getPath();
        String namespace = id.getNamespace();
        if (!namespace.equals("flywheel") && !namespace.equals("meshlet")
                && (path.startsWith("instance/") || path.startsWith("material/"))) {
            if (path.endsWith(".vert")) {
                return MARK + VERTEX_SHIM + (namespace.equals(GemRender.MOD_ID) && path.equals(VOLUME)
                        ? replaceOne(id, source, VOLUME_NEAR, VOLUME_NEAR_26) : source);
            }
            if (path.endsWith(".frag")) {
                String rewritten = DIFFUSE_TEX.matcher(source)
                        .replaceAll("gemrender_diffuse(");
                return MARK + FRAGMENT_SHIM + NO_OVERLAY.matcher(rewritten)
                        .replaceAll("flw_material.useOverlay = false;");
            }
            return source;
        }
        if (namespace.equals("flywheel")) {
            if (path.equals(INSTANCING_MAIN)) {
                return MARK + source + SELECTOR_BASE_VERTEX;
            }
            if (path.equals(INDIRECT_FRAGMENT) || path.equals(OIT_FRAGMENT)) {
                return MARK + source + DIFFUSE;
            }
        }
        return source;
    }

    private static String replaceOne(ResourceLocation id, String source, String target, String replacement) {
        int at = source.indexOf(target);
        if (at < 0) {
            throw new IllegalStateException(id + ": expected '" + target + "' for the CrankShaft 26.2 shim");
        }
        return source.substring(0, at) + replacement + source.substring(at + target.length());
    }
}
*///?}
