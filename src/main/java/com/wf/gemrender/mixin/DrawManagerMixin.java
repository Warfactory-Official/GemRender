package com.wf.gemrender.mixin;

import com.wf.gemrender.render.FrameUploads;
import dev.engine_room.flywheel.backend.engine.DrawManager;
import dev.engine_room.flywheel.backend.engine.LightStorage;
import dev.engine_room.flywheel.backend.engine.embed.EnvironmentStorage;
//? if >=26.2 {
/*import net.minecraft.core.Vec3i;
import org.joml.Matrix4fc;
*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DrawManager.class, remap = false)
public abstract class DrawManagerMixin {
    //? if >=26.2 {
    /*@Inject(method = "render", at = @At("TAIL"))
    private void gemrender$bindBoneBuffer(LightStorage lightStorage, EnvironmentStorage environmentStorage,
                                          Matrix4fc modelViewMatrix, Vec3i renderOrigin, boolean constantAmbientLight,
                                          CallbackInfo ci) {
        FrameUploads.run();
    }
    *///?} else {
    @Inject(method = "render", at = @At("TAIL"))
    private void gemrender$bindBoneBuffer(LightStorage lightStorage, EnvironmentStorage environmentStorage, CallbackInfo ci) {
        FrameUploads.run();
    }
    //?}
}
