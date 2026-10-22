package com.wf.gemrender.mixin.light;

import com.wf.gemrender.light.LightFrame;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every client block state change, whatever the update flags. */
@Mixin(ClientLevel.class)
abstract class ClientLevelMixin {
    @Inject(method = "setBlocksDirty", at = @At("HEAD"))
    private void gemrender$occupancy(BlockPos pos, BlockState oldState, BlockState newState, CallbackInfo ci) {
        LightFrame.blockChanged(pos, newState);
    }

    //? if >=26.1 {
    /*@Inject(method = "onChunkLoaded", at = @At("TAIL"))
    private void gemrender$occupancyLoad(net.minecraft.world.level.ChunkPos pos, CallbackInfo ci) {
        LightFrame.chunkChanged(pos.x(), pos.z());
    }
    *///?} elif <1.21 {
    /*/^* No LevelRenderer.onChunkLoaded before 1.21. ^/
    @Inject(method = "onChunkLoaded", at = @At("TAIL"))
    private void gemrender$occupancyLoad(net.minecraft.world.level.ChunkPos pos, CallbackInfo ci) {
        LightFrame.chunkChanged(pos.x, pos.z);
    }
    *///?}
}
