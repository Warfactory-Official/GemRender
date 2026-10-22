package com.wf.gemrender.mixin.light;

import com.wf.gemrender.light.LightShaders;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

//? if >=26.2 {
/*import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.shaders.ShaderType;
import net.minecraft.resources.ResourceLocation;

@Mixin(targets = "com.mojang.blaze3d.opengl.GlDevice", remap = false)
abstract class ProgramMixin {
    @Redirect(method = "compileShader", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/shaders/ShaderSource;get(Lnet/minecraft/resources/ResourceLocation;Lcom/mojang/blaze3d/shaders/ShaderType;)Ljava/lang/String;"))
    private String gemrender$patch(ShaderSource source, ResourceLocation id, ShaderType type) {
        String text = source.get(id, type);
        if (text != null && "sodium".equals(id.getNamespace())) {
            // Sodium 0.9 terrain = vanilla RenderPipeline; ids extensionless, unlike its old ShaderLoader.
            return LightShaders.sodium(id.withSuffix(type == ShaderType.VERTEX ? ".vsh" : ".fsh"), text);
        }
        return LightShaders.vanilla(id.getPath(), type == ShaderType.VERTEX, text);
    }
}
*///?} else {
import com.mojang.blaze3d.shaders.Program;
import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.InputStream;
import java.util.List;

@Mixin(Program.class)
abstract class ProgramMixin {
    @Unique
    private static String gemrender$name;

    @Unique
    private static boolean gemrender$vertex;

    @Inject(method = "compileShaderInternal", at = @At("HEAD"))
    private static void gemrender$capture(Program.Type type, String name, InputStream shaderData, String sourceName,
                                          GlslPreprocessor preprocessor, CallbackInfoReturnable<Integer> cir) {
        gemrender$name = name;
        gemrender$vertex = type == Program.Type.VERTEX;
    }

    @ModifyArg(method = "compileShaderInternal", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/GlStateManager;glShaderSource(ILjava/util/List;)V"), index = 1)
    private static List<String> gemrender$patch(List<String> source) {
        return List.of(LightShaders.vanilla(gemrender$name, gemrender$vertex, String.join("", source)));
    }
}
//?}
