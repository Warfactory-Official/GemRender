package com.wf.gemrender.mixin;

import com.wf.gemrender.render.FrameUploads;
import dev.engine_room.flywheel.backend.engine.DrawManager;
import dev.engine_room.flywheel.backend.engine.LightStorage;
import dev.engine_room.flywheel.backend.engine.embed.EnvironmentStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DrawManager.class, remap = false)
public abstract class DrawManagerMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void gemrender$bindBoneBuffer(LightStorage lightStorage, EnvironmentStorage environmentStorage, CallbackInfo ci) {
        FrameUploads.run();
    }
}
