package com.wf.gemrender.mixin.light;

import com.mojang.blaze3d.platform.GlStateManager;
import com.wf.gemrender.light.IrTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GlStateManager.class)
abstract class IrRenderStateMixin {
    @Inject(method = "_glUseProgram", at = @At("RETURN"))
    private static void gemrender$selectIrProgram(int program, CallbackInfo ci) {
        IrTarget.programChanged(program);
    }

    @Inject(method = "_glBindFramebuffer", at = @At("RETURN"))
    private static void gemrender$selectIrFramebuffer(int target, int framebuffer, CallbackInfo ci) {
        IrTarget.framebufferBound(target, framebuffer);
    }
}
