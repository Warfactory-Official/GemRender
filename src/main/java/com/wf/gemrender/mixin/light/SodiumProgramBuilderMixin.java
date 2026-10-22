package com.wf.gemrender.mixin.light;

import com.wf.gemrender.light.LightShaders;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Function;

/** Sodium 0.6+ and Sodium 0.5 (1.20.1). */
@Pseudo
@Mixin(targets = {"net.caffeinemc.mods.sodium.client.gl.shader.GlProgram$Builder",
        "me.jellysquid.mods.sodium.client.gl.shader.GlProgram$Builder"}, remap = false)
abstract class SodiumProgramBuilderMixin {
    @Shadow
    @Final
    private int program;

    @Inject(method = "link", at = @At("RETURN"))
    private void gemrender$bindLights(Function<?, ?> factory, CallbackInfoReturnable<Object> cir) {
        LightShaders.bindProgram(program);
    }
}
