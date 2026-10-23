package com.wf.gemrender.mixin.direct26;

import com.wf.gemrender.direct.DirectPass;
import com.wf.gemrender.direct.DirectRenderer;
import com.wf.gemrender.render.Vanilla;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PictureInPictureRenderer.class)
public class PictureInPictureRendererMixin {
    @Inject(method = "prepare(Lnet/minecraft/client/renderer/state/gui/pip/PictureInPictureRenderState;"
            + "Lnet/minecraft/client/renderer/state/gui/GuiRenderState;I)V", at = @At("HEAD"))
    private void gemrender$beginPip(PictureInPictureRenderState renderState, GuiRenderState guiRenderState,
                                    int guiScale, CallbackInfo ci) {
        DirectRenderer.beginSink(DirectPass.GUI);
    }

    @Inject(method = "prepare(Lnet/minecraft/client/renderer/state/gui/pip/PictureInPictureRenderState;"
            + "Lnet/minecraft/client/renderer/state/gui/GuiRenderState;I)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;endBatch()V",
                    shift = At.Shift.AFTER))
    private void gemrender$drawPip(PictureInPictureRenderState renderState, GuiRenderState guiRenderState,
                                   int guiScale, CallbackInfo ci) {

        Vanilla.intoOutputOverride(DirectRenderer::endSink);
    }

    @Inject(method = "prepare(Lnet/minecraft/client/renderer/state/gui/pip/PictureInPictureRenderState;"
            + "Lnet/minecraft/client/renderer/state/gui/GuiRenderState;I)V", at = @At("RETURN"))
    private void gemrender$endPip(PictureInPictureRenderState renderState, GuiRenderState guiRenderState,
                                  int guiScale, CallbackInfo ci) {
        Vanilla.intoOutputOverride(DirectRenderer::endSink);
    }
}
