package com.wf.gemrender.mixin.iris;

import com.wf.gemrender.direct.DirectPass;
import com.wf.gemrender.direct.DirectRenderer;
import net.irisshaders.iris.shadows.ShadowRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ShadowRenderer.class, remap = false)
abstract class ShadowRendererMixin {
    /** With the shadow entities, before Iris's translucent shadow terrain. */
    @Inject(method = "renderShadows", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;endBatch()V", remap = true))
    private void gemrender$flushDirect(CallbackInfo ci) {
        DirectRenderer.flush(DirectPass.SHADOW);
    }
}
