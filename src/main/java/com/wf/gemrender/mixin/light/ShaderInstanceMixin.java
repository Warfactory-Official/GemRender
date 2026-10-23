package com.wf.gemrender.mixin.light;

import com.wf.gemrender.light.LightShaders;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

//? if >=26.1 {
/*import com.mojang.blaze3d.opengl.GlProgram;
import com.mojang.blaze3d.pipeline.RenderPipeline;

import java.util.List;

/^* GlDevice.compileProgram, after vanilla bound its own blocks and samplers. ^/
@Mixin(value = GlProgram.class, remap = false)
abstract class ShaderInstanceMixin {
    @Inject(method = "setupUniforms", at = @At("RETURN"))
    private void gemrender$bindLights(List<RenderPipeline.UniformDescription> uniforms, List<String> samplers,
                                      CallbackInfo ci) {
        LightShaders.bindProgram(((GlProgram) (Object) this).getProgramId());
    }
}
*///?} else {
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.server.packs.resources.ResourceProvider;

@Mixin(ShaderInstance.class)
abstract class ShaderInstanceMixin {
    //? if fabric {
    /*/^* Vanilla's only constructor; the ResourceLocation one is NeoForge's and Forge's. ^/
    @Inject(method = "<init>(Lnet/minecraft/server/packs/resources/ResourceProvider;Ljava/lang/String;Lcom/mojang/blaze3d/vertex/VertexFormat;)V",
            at = @At("TAIL"))
    private void gemrender$bindLights(ResourceProvider provider, String name, VertexFormat format, CallbackInfo ci) {
        LightShaders.bindProgram(((ShaderInstance) (Object) this).getId());
    }
    *///?} else {
    @Inject(method = "<init>(Lnet/minecraft/server/packs/resources/ResourceProvider;Lnet/minecraft/resources/ResourceLocation;Lcom/mojang/blaze3d/vertex/VertexFormat;)V",
            at = @At("TAIL"))
    private void gemrender$bindLights(ResourceProvider provider, net.minecraft.resources.ResourceLocation location,
                                      VertexFormat format, CallbackInfo ci) {
        LightShaders.bindProgram(((ShaderInstance) (Object) this).getId());
    }
    //?}
}
//?}
