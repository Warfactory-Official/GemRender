package com.wf.gemrender.mixin.direct26;

import com.mojang.blaze3d.vertex.PoseStack;
import com.wf.gemrender.direct.DirectPass;
import com.wf.gemrender.direct.DirectRenderer;
import com.wf.gemrender.render.Vanilla;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
public class ScreenEffectRendererMixin {
    @Inject(method = "renderItemActivationAnimation(Lcom/mojang/blaze3d/vertex/PoseStack;F"
            + "Lnet/minecraft/client/renderer/SubmitNodeCollector;)V", at = @At("HEAD"))
    private void gemrender$beginItemActivation(PoseStack poseStack, float partialTicks,
                                               SubmitNodeCollector submitNodeCollector, CallbackInfo ci) {
        DirectRenderer.beginSink(DirectPass.HAND);
    }

    @Inject(method = "renderItemActivationAnimation(Lcom/mojang/blaze3d/vertex/PoseStack;F"
            + "Lnet/minecraft/client/renderer/SubmitNodeCollector;)V", at = @At("RETURN"))
    private void gemrender$endItemActivation(PoseStack poseStack, float partialTicks,
                                             SubmitNodeCollector submitNodeCollector, CallbackInfo ci) {

        Vanilla.intoMainTarget(DirectRenderer::endSink);
    }
}
