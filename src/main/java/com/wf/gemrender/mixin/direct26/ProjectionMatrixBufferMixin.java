package com.wf.gemrender.mixin.direct26;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.wf.gemrender.direct.DirectVanilla;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ProjectionMatrixBuffer.class)
public class ProjectionMatrixBufferMixin {
    @Inject(method = "writeBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;",
            at = @At("RETURN"))
    private void gemrender$recordProjection(Matrix4f projectionMatrix,
                                            CallbackInfoReturnable<GpuBufferSlice> cir) {
        DirectVanilla.recordProjection(cir.getReturnValue(), projectionMatrix);
    }
}
