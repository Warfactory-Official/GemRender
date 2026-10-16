package com.wf.gemrender.mixin.iris;

import com.google.common.collect.ImmutableSet;
import com.wf.gemrender.iris.guest.GuestBuild;
import net.irisshaders.iris.gl.image.ImageHolder;
import net.irisshaders.iris.gl.sampler.SamplerHolder;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

@Mixin(value = IrisRenderingPipeline.class, remap = false)
abstract class IrisRenderingPipelineMixin {
    @Inject(method = "addGbufferOrShadowSamplers", at = @At("HEAD"))
    private void gemrender$guestSamplers(SamplerHolder samplers, ImageHolder images,
                                         Supplier<ImmutableSet<Integer>> flipped, boolean isShadowPass,
                                         boolean hasTexture, boolean hasLightmap, boolean hasOverlay,
                                         CallbackInfo ci) {
        GuestBuild build = GuestBuild.current();
        if (build != null) {
            build.addSamplers(samplers);
        }
    }
}
