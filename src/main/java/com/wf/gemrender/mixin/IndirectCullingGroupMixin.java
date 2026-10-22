package com.wf.gemrender.mixin;

//? if <26.2 {
import com.wf.gemrender.water.Absorbance;
import dev.engine_room.flywheel.backend.compile.PipelineCompiler;
import dev.engine_room.flywheel.backend.engine.indirect.IndirectCullingGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = IndirectCullingGroup.class, remap = false)
abstract class IndirectCullingGroupMixin {

    //? if >=26.1 {
    /*@Inject(method = "submitOrderIndependent", at = @At("HEAD"), cancellable = true)
     *///?} else {
    @Inject(method = "submitTransparent", at = @At("HEAD"), cancellable = true)
    //?}
    private void gemrender$skipCoefficientPasses(PipelineCompiler.OitMode mode, CallbackInfo ci) {
        if (Absorbance.getInstance()
                .skips(mode)) {
            ci.cancel();
        }
    }
}
//?} else {
/*import dev.engine_room.flywheel.backend.engine.indirect.IndirectCullingGroup;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = IndirectCullingGroup.class, remap = false)
abstract class IndirectCullingGroupMixin {
}
*///?}
