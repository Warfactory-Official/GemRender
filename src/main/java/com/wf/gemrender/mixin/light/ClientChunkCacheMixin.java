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
    //? if >=1.21 {
    @Inject(method = "drop", at = @At("HEAD"))
    private void gemrender$occupancyDrop(ChunkPos pos, CallbackInfo ci) {
        //? if >=26.1 {
        /*LightFrame.chunkChanged(pos.x(), pos.z());
        *///?} else {
        LightFrame.chunkChanged(pos.x, pos.z);
        //?}
    }
    //?} else {
    /*@Inject(method = "drop", at = @At("HEAD"))
    private void gemrender$occupancyDrop(int x, int z, CallbackInfo ci) {
        LightFrame.chunkChanged(x, z);
    }
    *///?}
}
