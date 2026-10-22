package com.wf.gemrender.mixin.light;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

//? if >=26.2 {
/*import com.wf.gemrender.light.IrTarget;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.mojang.blaze3d.opengl.GlCommandEncoder", remap = false)
abstract class GlCommandEncoderMixin {
    @Inject(method = "trySetup", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/opengl/GL33C;glDrawBuffers([I)V", shift = At.Shift.AFTER))
    private void gemrender$keepIr(CallbackInfoReturnable<Boolean> cir) {
        IrTarget.refreshDrawBuffers();
    }
}
*///?}
