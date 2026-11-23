package com.wf.gemrender.mixin.ui26;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.wf.gemrender.ui.SharpText;
import net.minecraft.client.renderer.state.gui.GlyphRenderState;
import org.joml.Matrix3x2fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GlyphRenderState.class)
abstract class GlyphRenderStateMixin {
    @Shadow
    public abstract Matrix3x2fc pose();

    @Inject(method = "pipeline", at = @At("RETURN"), cancellable = true)
    private void gemrender$sharp(CallbackInfoReturnable<RenderPipeline> cir) {
        if (SharpText.marked(pose())) {
            cir.setReturnValue(SharpText.pipeline(cir.getReturnValue()));
        }
    }
}
