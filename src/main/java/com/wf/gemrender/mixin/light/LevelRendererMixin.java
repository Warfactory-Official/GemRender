package com.wf.gemrender.mixin.light;

import com.wf.gemrender.light.LightFrame;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if >=26.1 {
/*import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void gemrender$beginLights(GraphicsResourceAllocator resourceAllocator, DeltaTracker deltaTracker,
                                       boolean renderOutline, CameraRenderState cameraState,
                                       Matrix4fc modelViewMatrix, GpuBufferSlice terrainFog, Vector4f fogColor,
                                       boolean shouldRenderSky, ChunkSectionsToRender chunkSectionsToRender,
                                       CallbackInfo ci) {
        LightFrame.getInstance()
                .begin(cameraState.pos, new Matrix4f(modelViewMatrix), new Matrix4f(cameraState.projectionMatrix),
                        deltaTracker.getGameTimeDeltaPartialTick(false));
    }
}
*///?} elif >=1.21 {
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;

@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void gemrender$beginLights(DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera,
                                       GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f frustumMatrix,
                                       Matrix4f projectionMatrix, CallbackInfo ci) {
        LightFrame.getInstance()
                .begin(camera, frustumMatrix, projectionMatrix, deltaTracker.getGameTimeDeltaPartialTick(false));
    }

    @Inject(method = "onChunkLoaded", at = @At("TAIL"))
    private void gemrender$occupancyLoad(ChunkPos pos, CallbackInfo ci) {
        LightFrame.chunkChanged(pos.x, pos.z);
    }
}
//?} else {
/*import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;

/^* The chunk-load hook is ClientLevelMixin's here. ^/
@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void gemrender$beginLights(PoseStack poseStack, float partialTick, long finishNanoTime,
                                       boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer,
                                       LightTexture lightTexture, Matrix4f projectionMatrix, CallbackInfo ci) {
        LightFrame.getInstance()
                .begin(camera, new Matrix4f(poseStack.last().pose()), projectionMatrix, partialTick);
    }
}
*///?}
