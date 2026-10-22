package dev.engine_room.flywheel.impl.event;

import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL32;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.pipeline.RenderTarget;

import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.backend.engine.uniform.FogUniforms;
import dev.engine_room.flywheel.backend.mixin.LevelRendererAccessor;
import dev.engine_room.flywheel.backend.util.VanillaState;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelTerrainRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;

/**
 * Flywheel's entry into the level render on 26.1 Fabric, the counterpart of the NeoForge class of the
 * same name. Stages: START_MAIN for AfterSky, AFTER_SOLID_FEATURES for AfterOpaqueFeatures,
 * AFTER_TRANSLUCENT_TERRAIN for AfterTranslucentBlocks, END_MAIN for AfterLevel. The fog that NeoForge
 * hands over in ViewportEvent.RenderFog is read from the camera render state here.
 */
public final class FlwLevelRenderHooks {
	@Nullable
	private static RenderContextImpl context;

	private FlwLevelRenderHooks() {
	}

	public static void init() {
		LevelRenderEvents.START_MAIN.register(FlwLevelRenderHooks::onStartMain);
		LevelRenderEvents.AFTER_SOLID_FEATURES.register(FlwLevelRenderHooks::onAfterSolidFeatures);
		LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(FlwLevelRenderHooks::onAfterTranslucentTerrain);
		LevelRenderEvents.END_MAIN.register(event -> context = null);
	}

	private static void onStartMain(LevelTerrainRenderContext event) {
		LevelRenderer renderer = event.levelRenderer();
		var accessor = (LevelRendererAccessor) renderer;
		ClientLevel level = accessor.flywheel$getLevel();

		if (level == null) {
			context = null;
			return;
		}

		CameraRenderState cameraState = event.levelState().cameraRenderState;
		FogUniforms.onRenderFog(cameraState.fogData);

		Minecraft minecraft = Minecraft.getInstance();
		context = RenderContextImpl.create(renderer, level, accessor.flywheel$getRenderBuffers(),
				new Matrix4f(cameraState.viewRotationMatrix), LevelProjection.copy(),
				minecraft.gameRenderer.getMainCamera(), minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false));

		VisualizationManager manager = VisualizationManager.get(level);
		if (manager != null) {
			manager.renderDispatcher()
					.onStartLevelRender(context);
		}
	}

	private static void onAfterSolidFeatures(LevelRenderContext event) {
		intoMainTarget(() -> withManager(manager -> manager.renderDispatcher()
				.afterEntities(context)));
	}

	private static void onAfterTranslucentTerrain(LevelRenderContext event) {
		var accessor = (LevelRendererAccessor) event.levelRenderer();
		intoMainTarget(() -> withManager(manager -> manager.renderDispatcher()
				.beforeCrumbling(context, accessor.flywheel$getDestructionProgress())));
	}

	// 26.1 binds a framebuffer per RenderPass and leaves the last one bound between passes, so the
	// engine's raw-GL draws need the main target and its viewport bound around them, then restored.
	private static void intoMainTarget(Runnable draws) {
		RenderTarget target = Minecraft.getInstance()
				.getMainRenderTarget();
		int previousFbo = GlStateManager.getFrameBuffer(GL32.GL_FRAMEBUFFER);
		int[] previousViewport = new int[4];
		GL11C.glGetIntegerv(GL11C.GL_VIEWPORT, previousViewport);

		GlStateManager._glBindFramebuffer(GL32.GL_FRAMEBUFFER, VanillaState.fboOf(target));
		GlStateManager._viewport(0, 0, target.width, target.height);
		try {
			draws.run();
		} finally {
			GlStateManager._glBindFramebuffer(GL32.GL_FRAMEBUFFER, previousFbo);
			GlStateManager._viewport(previousViewport[0], previousViewport[1], previousViewport[2],
					previousViewport[3]);
		}
	}

	private static void withManager(Consumer<VisualizationManager> action) {
		if (context == null) {
			return;
		}

		VisualizationManager manager = VisualizationManager.get(context.level());
		if (manager != null) {
			action.accept(manager);
		}
	}
}
