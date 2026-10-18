package com.wf.gemrender.water;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.wf.gemrender.render.GlAudit;
import com.wf.gemrender.render.LevelStage;
import com.wf.gemrender.render.PassState;
import com.wf.gemrender.render.Vanilla;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11C;

import static org.lwjgl.opengl.GL11C.GL_NEAREST;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL11C.glTexParameteri;
import static org.lwjgl.opengl.GL12C.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL33C.GL_TEXTURE_2D;

//? if <26.1 {
import static org.lwjgl.opengl.GL11C.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_COMPONENT;
import static org.lwjgl.opengl.GL11C.GL_FLOAT;
import static org.lwjgl.opengl.GL11C.GL_LEQUAL;
import static org.lwjgl.opengl.GL11C.GL_NONE;
import static org.lwjgl.opengl.GL11C.glDeleteTextures;
import static org.lwjgl.opengl.GL11C.glDrawBuffer;
import static org.lwjgl.opengl.GL11C.glGenTextures;
import static org.lwjgl.opengl.GL11C.glReadBuffer;
import static org.lwjgl.opengl.GL11C.glTexImage2D;
import static org.lwjgl.opengl.GL11C.GL_ALWAYS;
import static org.lwjgl.opengl.GL11C.GL_EQUAL;
import static org.lwjgl.opengl.GL11C.GL_KEEP;
import static org.lwjgl.opengl.GL11C.GL_LESS;
import static org.lwjgl.opengl.GL11C.GL_REPLACE;
import static org.lwjgl.opengl.GL11C.GL_STENCIL_BUFFER_BIT;
import static org.lwjgl.opengl.GL11C.GL_STENCIL_TEST;
import static org.lwjgl.opengl.GL11C.glDisable;
import static org.lwjgl.opengl.GL11C.glEnable;
import static org.lwjgl.opengl.GL30C.GL_DEPTH32F_STENCIL8;
import static org.lwjgl.opengl.GL30C.GL_DEPTH_ATTACHMENT;
import static org.lwjgl.opengl.GL30C.GL_DEPTH_COMPONENT32F;
import static org.lwjgl.opengl.GL30C.GL_DEPTH_STENCIL;
import static org.lwjgl.opengl.GL30C.GL_DEPTH_STENCIL_ATTACHMENT;
import static org.lwjgl.opengl.GL30C.GL_FLOAT_32_UNSIGNED_INT_24_8_REV;
import static org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30C.glDeleteFramebuffers;
import static org.lwjgl.opengl.GL30C.glGenFramebuffers;
import static org.lwjgl.opengl.GL32C.glFramebufferTexture;
//?}
//? if <26.1 {
import com.wf.gemrender.mixin.LevelRendererAccessor;
import com.wf.gemrender.render.GlState;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
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

final class WaterDepthPrepass {

    //? if >=26.1 {
    /*static final boolean SUPPORTED = "force".equalsIgnoreCase(System.getProperty("gemrender.watersplit"));
     *///?} else {
    static final boolean SUPPORTED = true;
    //?}

    //? if >=26.1 {
    /*static final boolean CLOUDS_SUPPORTED = "force".equalsIgnoreCase(System.getProperty("gemrender.cloudsplit"));
     *///?} else {
    static final boolean CLOUDS_SUPPORTED = true;
    //?}
    private int width = -1;
    private int height = -1;

    private boolean rendering;

    private final PassState state = new PassState();

    //? if >=26.1 {
    /*private TextureTarget target;
     *///?} else {
    private int fbo;
    private int depthTexture;

    private int cloudFbo;
    private int cloudDepthTexture;
    //?}

    private boolean foldedClouds;

    //? if <26.1 {
    private static final double CLOUD_SLAB_REACH = 1.0e6;

    private static final double CLOUD_SLAB_MARGIN = 16.0;
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
	
	/*private void replay(LevelStage stage, RenderTarget main) {
		ChunkSectionsToRender chunks = stage.chunkSections();
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

    /**
     * @return false => nothing translucent and no folded cloud on screen: no split this frame. Else split depth
     * = water / cloud where in front of the scene, 0 elsewhere (front resubmit depth-rejected there; behind
     * composite takes the whole stack).
     */
    boolean run(LevelStage stage, WaterSplitPrograms programs, boolean foldClouds) {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        ensureSize(main.width, main.height);
        foldedClouds = false;

        GlAudit.Scope audit = GlAudit.open("water:prepass");

        state.saveDeep();
        rendering = true;
        try {
            //? if >=26.1 {
            /*replay(stage, main);
            return true;
             *///?} else {

            foldedClouds = foldClouds && drawCloudDepth(stage);
            if (!foldedClouds && !translucentVisible(stage)) {
                return false;
            }
            redraw(stage, programs, main, foldedClouds ? cloudDepthTexture : 0);
            return true;
            //?}
        } finally {
            rendering = false;

            Vanilla.bindWrite(main);
            state.restore();
            audit.close();
        }
    }

    //? if <26.1 {
    private void redraw(LevelStage stage, WaterSplitPrograms programs, RenderTarget main,
                        int cloudDepth) {
        GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, fbo);

        GlStateManager._disableBlend();
        GlStateManager._enableDepthTest();
        GlStateManager._depthMask(true);
        GlStateManager._stencilMask(0xFF);
        GlStateManager._clearStencil(0);
        GlState.clear(GL_STENCIL_BUFFER_BIT);
        GlStateManager._depthFunc(GL_ALWAYS);
        int scene = Vanilla.depthTextureId(main);
        programs.drawDepthCopy(scene, 0);

        // Stencil 1 = cloud / translucent in front of the scene.
        glEnable(GL_STENCIL_TEST);
        try {
            GlStateManager._stencilFunc(GL_ALWAYS, 1, 0xFF);
            GlStateManager._stencilOp(GL_KEEP, GL_KEEP, GL_REPLACE);
            if (cloudDepth != 0) {
                GlStateManager._depthFunc(GL_LESS);
                programs.drawDepthCopy(scene, cloudDepth);
            }
            GlStateManager._depthFunc(GL_LEQUAL);

            //? if >=1.21 {
            var camera = stage.camera()
                    .getPosition();
            ((LevelRendererAccessor) stage.levelRenderer()).gemrender$renderSectionLayer(
                    RenderType.translucent(), camera.x, camera.y, camera.z, stage.modelViewMatrix(),
                    stage.projectionMatrix());
            //?} else {
		/*var camera = stage.camera()
				.getPosition();
		((LevelRendererAccessor) stage.levelRenderer()).gemrender$renderSectionLayer(
				RenderType.translucent(), stage.poseStack(), camera.x, camera.y, camera.z,
				stage.projectionMatrix());
*///?}

            GlStateManager._stencilFunc(GL_EQUAL, 0, 0xFF);
            GlStateManager._stencilOp(GL_KEEP, GL_KEEP, GL_KEEP);
            GlStateManager._depthMask(true);
            GlStateManager._depthFunc(GL_ALWAYS);
            programs.drawDepthFill();
        } finally {
            GlStateManager._stencilFunc(GL_ALWAYS, 0, 0xFF);
            GlStateManager._stencilOp(GL_KEEP, GL_KEEP, GL_KEEP);
            glDisable(GL_STENCIL_TEST);
        }
    }

    private static boolean translucentVisible(LevelStage stage) {
        //? if >=1.21 {
        RenderType translucent = RenderType.translucent();
        for (var section : ((LevelRendererAccessor) stage.levelRenderer()).gemrender$visibleSections()) {
            if (!section.getCompiled()
                    .isEmpty(translucent)) {
                return true;
            }
        }
        return false;
        //?} else {
        /*// TODO 1.20.1: LevelRenderer.RenderChunkInfo package-private; gate needs an accessor on it.
        return true;
        *///?}
    }

    private boolean drawCloudDepth(LevelStage stage) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.options.getCloudsType() == CloudStatus.OFF) {
            return false;
        }
        float cloudHeight = mc.level.effects()
                .getCloudHeight();
        if (Float.isNaN(cloudHeight) || !stage.frustum()
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

        var camera = stage.camera()
                .getPosition();

        //? if >=1.21 {
        mc.levelRenderer.renderClouds(stage.poseStack(), stage.modelViewMatrix(),
                stage.projectionMatrix(), Vanilla.partialTick(), camera.x, camera.y, camera.z);
        //?} else {
		/*mc.levelRenderer.renderClouds(stage.poseStack(), stage.projectionMatrix(),
				Vanilla.partialTick(), camera.x, camera.y, camera.z);
*///?}
        return true;
    }

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
    //?}

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
            glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH32F_STENCIL8, width, height, 0, GL_DEPTH_STENCIL,
                    GL_FLOAT_32_UNSIGNED_INT_24_8_REV, (java.nio.ByteBuffer) null);
            setSamplingParameters();
        } finally {
            GlStateManager._bindTexture(previousTexture);
        }

        fbo = glGenFramebuffers();
        GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glFramebufferTexture(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, depthTexture, 0);

        glDrawBuffer(GL_NONE);
        glReadBuffer(GL_NONE);
        //?}
    }
}
