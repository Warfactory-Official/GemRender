package com.wf.gemrender.iris.guest;

import com.wf.gemrender.GemRender;
import com.wf.gemrender.mixin.iris.BlendModeOverrideAccessor;
import net.irisshaders.iris.gl.blending.BlendMode;
import net.irisshaders.iris.gl.blending.BlendModeOverride;
import net.irisshaders.iris.gl.blending.BufferBlendInformation;
import net.irisshaders.iris.gl.texture.InternalTextureFormat;
import net.irisshaders.iris.gl.texture.PixelFormat;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;
import java.util.regex.Pattern;

import static org.lwjgl.opengl.GL11C.GL_ONE;
import static org.lwjgl.opengl.GL11C.GL_ONE_MINUS_DST_ALPHA;
import static org.lwjgl.opengl.GL11C.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11C.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11C.GL_ZERO;

/**
 * Wavelet OIT over a pack's own translucent program: CrankShaft {@code ContractProperties.nativeForwardOit}, no
 * adapter spec. Every draw buffer accumulated, its own coefficient set; {@code null} => native translucency.
 */
record ForwardOit(int[] drawBuffers, int[] ranks, boolean[] premultiplied) {
    private static final Pattern COMMENT = Pattern.compile("//[^\\n]*|/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern SIDE_EFFECT = Pattern.compile(
            "\\b(buffer|imageStore|imageAtomic\\w*|atomicCounter\\w*|atomic\\w*|gl_FragDepth)\\b");
    private static final int MAX_BUFFERS = 8;

    /** Coefficient set {@code slot} = draw buffer {@code slot}. */
    int sets() {
        return ranks.length;
    }

    @Nullable
    static ForwardOit of(ProgramSource source) {
        if (!source.isValid() || source.getGeometrySource()
                .isPresent() || source.getTessControlSource()
                .isPresent() || source.getTessEvalSource()
                .isPresent() || source.getDirectives()
                .hasUnknownDrawBuffers()) {
            return reject(source, "extra stages or unknown draw buffers");
        }
        String stages = COMMENT.matcher(source.getVertexSource()
                        .orElseThrow() + '\n' + source.getFragmentSource()
                        .orElseThrow())
                .replaceAll(" ");
        if (SIDE_EFFECT.matcher(stages)
                .find()) {
            return reject(source, "depth or storage side effects");
        }
        int[] buffers = source.getDirectives()
                .getDrawBuffers();
        if (buffers.length == 0 || buffers.length > MAX_BUFFERS) {
            return reject(source, buffers.length + " draw buffers");
        }

        BlendModeOverride override = source.getDirectives()
                .getBlendModeOverride()
                .orElse(null);
        BlendMode global = override == null
                ? new BlendMode(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA)
                : ((BlendModeOverrideAccessor) override).gemrender$blendMode();

        boolean[] premultiplied = new boolean[buffers.length];
        for (int slot = 0; slot < buffers.length; slot++) {
            InternalTextureFormat format = Objects.requireNonNull(source.getParent()
                            .getPackDirectives()
                            .getRenderTargetDirectives()
                            .getRenderTargetSettings()
                            .get(buffers[slot]))
                    .getInternalFormat();
            String name = format.name();
            if (name.endsWith("I") || name.endsWith("_SNORM")) {
                return reject(source, "colortex" + buffers[slot] + " has unsupported format " + format);
            }
            // Float targets need an opacity contract (CrankShaft: certified adapter); none here.
            if (name.contains("F") || format == InternalTextureFormat.RGB9_E5) {
                return reject(source, "colortex" + buffers[slot] + " is floating point (" + format + ")");
            }
            BlendMode mode = global;
            for (BufferBlendInformation buffer : source.getDirectives()
                    .getBufferBlendOverrides()) {
                if (buffer.index() == buffers[slot]) {
                    mode = buffer.blendMode();
                }
            }
            // RGB targets discard alpha, so their alpha factors do not affect storage.
            if (mode == null || mode.dstRgb() != GL_ONE_MINUS_SRC_ALPHA
                    || mode.srcRgb() != GL_SRC_ALPHA && mode.srcRgb() != GL_ONE
                    || format.getPixelFormat() != PixelFormat.RGB && !sourceOverAlpha(mode)) {
                return reject(source, "colortex" + buffers[slot] + " is not source-over");
            }
            premultiplied[slot] = mode.srcRgb() == GL_ONE;
        }

        int[] ranks = new int[buffers.length];
        Arrays.fill(ranks, buffers.length <= 2 ? 3 : buffers.length <= 4 ? 2 : 1);
        GemRender.LOGGER.info("Guest OIT on {}: targets {} ranks {}", source.getName(), Arrays.toString(buffers),
                Arrays.toString(ranks));
        return new ForwardOit(buffers, ranks, premultiplied);
    }

    private static boolean sourceOverAlpha(BlendMode mode) {
        return mode.srcAlpha() == GL_ONE && mode.dstAlpha() == GL_ONE_MINUS_SRC_ALPHA
                || mode.srcAlpha() == GL_ONE_MINUS_DST_ALPHA && mode.dstAlpha() == GL_ONE
                || mode.srcAlpha() == GL_ZERO && mode.dstAlpha() == GL_ONE;
    }

    @Nullable
    private static ForwardOit reject(ProgramSource source, String reason) {
        GemRender.LOGGER.info("Guest OIT off for {}: {}; native translucency", source.getName(), reason);
        return null;
    }

    /** {@code (1 << (rank + 1)) / 4} RGBA16F layers. */
    static int layers(int rank) {
        return (1 << (rank + 1)) / 4;
    }
}
