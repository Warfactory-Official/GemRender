package com.wf.gemrender.mixin.ui26;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.wf.gemrender.ui.CanvasPip;
import com.wf.gemrender.ui.SharpText;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderer.class)
abstract class GuiRendererMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void gemrender$frame(GpuBufferSlice fogBuffer, CallbackInfo ci) {
        CanvasPip.nextFrame();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void gemrender$frameEnd(GpuBufferSlice fogBuffer, CallbackInfo ci) {
        SharpText.clearMarks();
    }
}
