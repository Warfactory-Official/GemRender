package com.wf.gemrender.mixin.ui26;

import com.wf.gemrender.ui.SharpText;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import org.joml.Matrix3x2fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiTextRenderState.class)
abstract class GuiTextRenderStateMixin {
    @Shadow
    @Final
    public Matrix3x2fc pose;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void gemrender$mark(CallbackInfo ci) {
        if (SharpText.active()) {
            SharpText.mark(pose);
        }
    }
}
