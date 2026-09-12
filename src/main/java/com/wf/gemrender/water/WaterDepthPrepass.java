package com.wf.gemrender.water;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.wf.gemrender.mixin.LevelRendererAccessor;
import com.wf.gemrender.render.GlAudit;
import com.wf.gemrender.render.GlState;
import com.wf.gemrender.render.Vanilla;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.lwjgl.opengl.GL11C;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL12C.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL30C.*;
import static org.lwjgl.opengl.GL32C.glFramebufferTexture;
import static org.lwjgl.opengl.GL33C.GL_TEXTURE_2D;

//? if <26.1 {
//?}
//? if <26.1 {
//?}
//? if >=26.1 {
/*import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
*/
//?}
//? if neoforge {
//?} else {
/*import net.minecraftforge.client.event.RenderLevelStageEvent;
 *///?}

final class WaterDepthPrepass {

    //? if >=26.1 {
    /*static final boolean SUPPORTED = false;
     *///?} else {
    static final boolean SUPPORTED = true;
    //?}

    //? if >=26.1 {
    /*static final boolean CLOUDS_SUPPORTED = false;
     *///?} else {
    static final boolean CLOUDS_SUPPORTED = true;
    //?}
    private static final double CLOUD_SLAB_REACH = 1.0e6;
    private static final double CLOUD_SLAB_MARGIN = 16.0;
    private final PassState state = new PassState();
    private int width = -1;

    //? if >=26.1 {
    private int height = -1;
    private boolean rendering;
    /*private TextureTarget target;
     *///?} else {
    private int fbo;
    private int depthTexture;
    //?}
    private int cloudFbo;

    //? if <26.1 {
    private int cloudDepthTexture;
    private boolean foldedClouds;
    //?}

    private static void setSamplingParameters() {
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    }

    int textureId() {
        //? if >=26.1 {
        /*return target == null ? 0 : Vanilla.depthTextureId(target);
         *///?} else {
        return depthTexture;
        //?}
    }

    int cloudTextureId() {
        //? if >=26.1 {
        /*return 0;
         *///?} else {
        return cloudDepthTexture;
        //?}
    }

    boolean foldedClouds() {
        return foldedClouds;
    }

    boolean isRendering() {
        return rendering;
    }

    //? if >=26.1 {
	
	/*private void replay(RenderLevelStageEvent event, RenderTarget main) {
		ChunkSectionsToRender chunks = event.getLevelRenderState().chunkSectionsToRender;
		if (chunks == null) {
			return;
		}

		target.copyDepthFrom(main);

		var drawGroups = chunks.drawGroupsPerLayer()
				.get(ChunkSectionLayer.TRANSLUCENT);
		if (drawGroups == null || drawGroups.isEmpty()) {
			return;
		}

		var autoIndices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
		int maxIndices = chunks.maxIndicesRequired();
		GpuBuffer indexBuffer = maxIndices == 0 ? null : autoIndices.getBuffer(maxIndices);
		VertexFormat.IndexType indexType = maxIndices == 0 ? null : autoIndices.type();

		Minecraft mc = Minecraft.getInstance();
		try (RenderPass pass = RenderSystem.getDevice()
				.createCommandEncoder()
				.createRenderPass(() -> "GemRender water depth prepass", target.getColorTextureView(),
						OptionalInt.empty(), target.getDepthTextureView(), OptionalDouble.empty())) {
			RenderSystem.bindDefaultUniforms(pass);

			pass.bindTexture("Sampler0", chunks.textureView(), RenderSystem.getSamplerCache()
					.getClampToEdge(FilterMode.LINEAR, true));
			pass.bindTexture("Sampler2", mc.gameRenderer.lightmap(), RenderSystem.getSamplerCache()
					.getClampToEdge(FilterMode.LINEAR));
			pass.setPipeline(ChunkSectionLayer.TRANSLUCENT.pipeline());

			for (var draws : drawGroups.values()) {
				if (!draws.isEmpty()) {
					pass.drawMultipleIndexed(draws.reversed(), indexBuffer, indexType,
							List.of("ChunkSection"), chunks.chunkSectionInfos());
				}
			}
		}
	}
*///?}

    //? if <26.1 {

    void run(RenderLevelStageEvent event, WaterSplitPrograms programs, boolean foldClouds) {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        ensureSize(main.width, main.height);
        foldedClouds = false;

        GlAudit.Scope audit = GlAudit.open("water:prepass");

        state.saveDeep();
        rendering = true;
        try {
            //? if >=26.1 {
            /*replay(event, main);
             *///?} else {

            foldedClouds = foldClouds && drawCloudDepth(event);
            redraw(event, programs, main, foldedClouds ? cloudDepthTexture : 0);
            //?}
        } finally {
            rendering = false;

            Vanilla.bindWrite(main);
            state.restore();
            audit.close();
        }
    }

    private void redraw(RenderLevelStageEvent event, WaterSplitPrograms programs, RenderTarget main,
                        int cloudDepth) {
        GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, fbo);

        GlStateManager._disableBlend();
        GlStateManager._enableDepthTest();
        GlStateManager._depthFunc(GL11C.GL_ALWAYS);
        GlStateManager._depthMask(true);
        programs.drawDepthCopy(Vanilla.depthTextureId(main), cloudDepth);
        GlStateManager._depthFunc(GL_LEQUAL);

        //? if >=1.21 {
        var camera = event.getCamera()
                .getPosition();
        ((LevelRendererAccessor) event.getLevelRenderer()).gemrender$renderSectionLayer(
                RenderType.translucent(), camera.x, camera.y, camera.z, event.getModelViewMatrix(),
                event.getProjectionMatrix());
        //?} else {
		/*var camera = event.getCamera()
				.getPosition();
		((LevelRendererAccessor) event.getLevelRenderer()).gemrender$renderSectionLayer(
				RenderType.translucent(), event.getPoseStack(), camera.x, camera.y, camera.z,
				event.getProjectionMatrix());
*///?}
    }

    private boolean drawCloudDepth(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.options.getCloudsType() == CloudStatus.OFF) {
            return false;
        }
        float cloudHeight = mc.level.effects()
                .getCloudHeight();
        if (Float.isNaN(cloudHeight) || !event.getFrustum()
                .isVisible(new AABB(-CLOUD_SLAB_REACH, cloudHeight - CLOUD_SLAB_MARGIN, -CLOUD_SLAB_REACH,
                        CLOUD_SLAB_REACH, cloudHeight + CLOUD_SLAB_MARGIN, CLOUD_SLAB_REACH))) {
            return false;
        }
        ensureCloudTarget();

        GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, cloudFbo);
        GlStateManager._disableBlend();
        GlStateManager._enableDepthTest();
        GlStateManager._depthFunc(GL_LEQUAL);

        GlStateManager._depthMask(true);
        GlStateManager._clearDepth(1.0);
        GlState.clear(GL_DEPTH_BUFFER_BIT);

        var camera = event.getCamera()
                .getPosition();

        //? if >=1.21 {
        mc.levelRenderer.renderClouds(event.getPoseStack(), event.getModelViewMatrix(),
                event.getProjectionMatrix(), Vanilla.partialTick(), camera.x, camera.y, camera.z);
        //?} else {
		/*mc.levelRenderer.renderClouds(event.getPoseStack(), event.getProjectionMatrix(),
				Vanilla.partialTick(), camera.x, camera.y, camera.z);
*///?}
        return true;
    }
    //?}

    private void ensureCloudTarget() {
        if (cloudDepthTexture != 0) {
            return;
        }
        cloudDepthTexture = glGenTextures();

        int previousTexture = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
        try {
            GlStateManager._bindTexture(cloudDepthTexture);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT32F, width, height, 0, GL_DEPTH_COMPONENT,
                    GL_FLOAT, (java.nio.ByteBuffer) null);
            setSamplingParameters();
        } finally {
            GlStateManager._bindTexture(previousTexture);
        }

        cloudFbo = glGenFramebuffers();
        GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, cloudFbo);
        glFramebufferTexture(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, cloudDepthTexture, 0);

        glDrawBuffer(GL_NONE);
        glReadBuffer(GL_NONE);
    }

    private void ensureSize(int newWidth, int newHeight) {
        if (width == newWidth && height == newHeight) {
            return;
        }
        width = newWidth;
        height = newHeight;

        //? if >=26.1 {
		/*if (target != null) {
			target.destroyBuffers();
		}
		target = new TextureTarget("GemRender water prepass", width, height, true);

		int previousTexture = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
		try {
			GlStateManager._bindTexture(Vanilla.depthTextureId(target));
			setSamplingParameters();
		} finally {
			GlStateManager._bindTexture(previousTexture);
		}
*///?} else {
        if (depthTexture != 0) {
            glDeleteTextures(depthTexture);
            glDeleteFramebuffers(fbo);
        }

        if (cloudDepthTexture != 0) {
            glDeleteTextures(cloudDepthTexture);
            glDeleteFramebuffers(cloudFbo);
            cloudDepthTexture = 0;
            cloudFbo = 0;
        }

        depthTexture = glGenTextures();

        int previousTexture = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
        try {
            GlStateManager._bindTexture(depthTexture);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT32F, width, height, 0, GL_DEPTH_COMPONENT,
                    GL_FLOAT, (java.nio.ByteBuffer) null);
            setSamplingParameters();
        } finally {
            GlStateManager._bindTexture(previousTexture);
        }

        fbo = glGenFramebuffers();
        GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glFramebufferTexture(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, depthTexture, 0);

        glDrawBuffer(GL_NONE);
        glReadBuffer(GL_NONE);
        //?}
    }
}
