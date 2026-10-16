package com.wf.gemrender.mixin.iris;

import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.shaderpack.programs.ProgramFallbackResolver;
import net.irisshaders.iris.shadows.ShadowRenderTargets;
import net.irisshaders.iris.targets.RenderTargets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = IrisRenderingPipeline.class, remap = false)
public interface IrisRenderingPipelineAccessor {
    @Accessor("renderTargets")
    RenderTargets gemrender$renderTargets();

    @Accessor("resolver")
    ProgramFallbackResolver gemrender$resolver();

    @Accessor("shadowRenderTargets")
    ShadowRenderTargets gemrender$shadowRenderTargets();
}
