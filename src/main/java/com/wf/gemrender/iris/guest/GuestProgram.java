package com.wf.gemrender.iris.guest;

import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.programs.ExtendedShader;

/** {@code before == after} in the shadow pass. */
record GuestProgram(ExtendedShader shader, GlFramebuffer before, GlFramebuffer after) {
    /** What {@code shader.apply()} binds. */
    GlFramebuffer target(IrisRenderingPipeline pipeline) {
        return pipeline.isBeforeTranslucent ? before : after;
    }

    void close() {
        shader.close();
        before.destroy();
        if (after != before) {
            after.destroy();
        }
    }
}
