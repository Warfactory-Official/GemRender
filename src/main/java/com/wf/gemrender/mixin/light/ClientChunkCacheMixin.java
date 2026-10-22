package com.wf.gemrender.mixin.light;

import com.wf.gemrender.light.LightFrame;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientChunkCache.class)
abstract class ClientChunkCacheMixin {
    @Inject(method = "drop", at = @At("HEAD"))
    private void gemrender$occupancyDrop(ChunkPos pos, CallbackInfo ci) {
        //? if >=26.2 {
        /*LightFrame.chunkChanged(pos.x(), pos.z());
        *///?} else {
        LightFrame.chunkChanged(pos.x, pos.z);
        //?}
    }
}
