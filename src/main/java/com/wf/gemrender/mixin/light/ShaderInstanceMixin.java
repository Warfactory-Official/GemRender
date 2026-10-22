package com.wf.gemrender.mixin.light;

import com.wf.gemrender.light.LightShaders;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

//? if >=26.2 {
/*import com.mojang.blaze3d.opengl.GlProgram;
import com.mojang.blaze3d.pipeline.BindGroupLayout;

import java.util.List;

/^* GlDevice.compileProgram and pipelines linked outside it (CrankShaft's RenderPipelineCompiler) both end here. ^/
@Mixin(value = GlProgram.class, remap = false)
abstract class ShaderInstanceMixin {
    @Inject(method = "setupBindGroupLayouts", at = @At("RETURN"))
    private void gemrender$bindLights(List<BindGroupLayout> bindGroupLayouts, CallbackInfo ci) {
        LightShaders.bindProgram(((GlProgram) (Object) this).getProgramId());
    }
}
*///?} else {
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;

@Mixin(ShaderInstance.class)
abstract class ShaderInstanceMixin {
    @Inject(method = "<init>(Lnet/minecraft/server/packs/resources/ResourceProvider;Lnet/minecraft/resources/ResourceLocation;Lcom/mojang/blaze3d/vertex/VertexFormat;)V",
            at = @At("TAIL"))
    private void gemrender$bindLights(ResourceProvider provider, ResourceLocation location, VertexFormat format,
                                      CallbackInfo ci) {
        LightShaders.bindProgram(((ShaderInstance) (Object) this).getId());
    }
}
//?}
