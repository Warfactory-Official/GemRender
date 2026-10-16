package com.wf.gemrender.iris.guest;

import com.wf.gemrender.direct.DirectGuest;
import com.wf.gemrender.direct.DirectPass;
import com.wf.gemrender.direct.DirectRenderer;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.shadows.ShadowRenderer;

import java.util.List;

/** Loaded only once Iris is known present. */
public final class IrisGuest implements DirectGuest {
    private IrisGuest() {
    }

    public static void install() {
        DirectRenderer.installGuest(new IrisGuest());
    }

    @Override
    public boolean active() {
        return Iris.getPipelineManager()
                .getPipelineNullable() instanceof IrisRenderingPipeline;
    }

    @Override
    public boolean shadowPass() {
        return ShadowRenderer.ACTIVE;
    }

    @Override
    public void flush(DirectPass pass, List<? extends Batch> batches) {
        GuestRenderer.flush((IrisRenderingPipeline) Iris.getPipelineManager()
                .getPipelineNullable(), pass, batches);
    }

    @Override
    public void release() {
        GuestPrograms.release();
    }
}
