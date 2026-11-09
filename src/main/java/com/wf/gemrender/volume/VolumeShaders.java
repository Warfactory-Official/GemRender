package com.wf.gemrender.volume;

import com.wf.gemrender.Ids;
import net.minecraft.resources.ResourceLocation;

public final class VolumeShaders {
    private static final ResourceLocation COMMON = Ids.of("flywheel", "internal/common.frag");
    private static final String DEPTH = "float linearDepth = linear_depth();";

    private VolumeShaders() {
    }

    /** The coverage quad's raster depth is unrelated to where the ray first meets the effect. */
    public static String patch(ResourceLocation id, String source) {
        if (!COMMON.equals(id)) return source;
        if (source.indexOf(DEPTH) < 0 || source.indexOf(DEPTH) != source.lastIndexOf(DEPTH)) {
            throw new IllegalStateException("GemRender volumes: expected one Flywheel linear depth calculation");
        }
        return source.replace(DEPTH, """
                #ifdef GEMRENDER_VOLUME_EFFECT
                    float linearDepth = gemrender_effectViewDepth;
                #else
                    float linearDepth = linear_depth();
                #endif
                """);
    }
}
