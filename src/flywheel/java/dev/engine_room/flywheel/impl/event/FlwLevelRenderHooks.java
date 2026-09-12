package dev.engine_room.flywheel.impl.event;

import org.jetbrains.annotations.Nullable;

import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL32;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;

import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.backend.mixin.LevelRendererAccessor;
import dev.engine_room.flywheel.backend.util.VanillaState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Flywheel's entry into the level render on 26.1.
 * <p>
 * Upstream injects into {@code LevelRenderer#renderLevel} at three points. 26.1 rebuilt that method
 * around a frame graph and an extract/submit split, and every one of those injection points is gone.
 * NeoForge fires {@link RenderLevelStageEvent} at the equivalent moments and -- importantly -- hands
 * the listener the {@code LevelRenderState} the new pipeline is rendering from, which is the only
 * public route to the per-frame camera and sky data the backend's uniforms need. Driving the engine
 * from the event rather than a mixin is also what NTM-CE's own 26.1 instancing engine does.
 * <p>
 * The stage mapping, against the old injection points:
 * <ul>
 * <li>{@link RenderLevelStageEvent.AfterSky} -- first stage of the frame, stands in for the old
 * {@code runLightUpdates} hook that started the render.</li>
 * <li>{@link RenderLevelStageEvent.AfterOpaqueFeatures} -- after entity and block-entity opaque
 * geometry, standing in for the old {@code popPush("blockentities")} marker.</li>
 * <li>{@link RenderLevelStageEvent.AfterTranslucentBlocks} -- stands in for the old
 * {@code popPush("destroyProgress")} marker.</li>
 * <li>{@link RenderLevelStageEvent.AfterLevel} -- end of frame.</li>
 * </ul>
 * <p>
 * <b>Unverified at runtime.</b> The stage choices above are reasoned from 26.1's LevelRenderer, not
 * measured -- 26.1 has never been launched. See gemrender-internal/docs/MULTIVERSION.md.
 */
public final class FlwLevelRenderHooks {
	@Nullable
	private static RenderContextImpl context;

	private FlwLevelRenderHooks() {
	}

	public static void onAfterSky(RenderLevelStageEvent.AfterSky event) {
		LevelRenderer renderer = event.getLevelRenderer();
		var accessor = (LevelRendererAccessor) renderer;
		ClientLevel level = accessor.flywheel$getLevel();

		if (level == null) {
			context = null;
			return;
		}

		var cameraState = event.getLevelRenderState().cameraRenderState;
		context = RenderContextImpl.create(renderer, level, accessor.flywheel$getRenderBuffers(), event.getModelViewMatrix(), cameraState.projectionMatrix, Minecraft.getInstance()
				.gameRenderer
				.getMainCamera(), Minecraft.getInstance()
				.getDeltaTracker()
				.getGameTimeDeltaPartialTick(false));

		VisualizationManager manager = VisualizationManager.get(level);
		if (manager != null) {
			manager.renderDispatcher()
					.onStartLevelRender(context);
		}
	}

	public static void onAfterOpaqueFeatures(RenderLevelStageEvent.AfterOpaqueFeatures event) {
		intoMainTarget(() -> withManager(manager -> manager.renderDispatcher()
				.afterEntities(context)));
	}

	public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
		var accessor = (LevelRendererAccessor) event.getLevelRenderer();
		intoMainTarget(() -> withManager(manager -> manager.renderDispatcher()
				.beforeCrumbling(context, accessor.flywheel$getDestructionProgress())));
	}

	/**
	 * Runs the engine's draws with vanilla's main render target bound.
	 *
	 * <p>This is the difference between drawing and not drawing on 26.1, and it is easy to miss because
	 * nothing about it is an error. Before 26.1 the whole of {@code LevelRenderer#renderLevel} ran with
	 * the main target bound, so Flywheel's raw-GL draws simply inherited it and upstream never binds
	 * anything for the ordinary instancing path -- only {@code OitFramebuffer} binds, because it wants a
	 * target of its own. 26.1 has no ambient framebuffer: the frame graph binds one inside each
	 * {@code RenderPass} and leaves whatever was last bound in place between them, and these events fire
	 * <em>between</em> passes. The draws then land in a framebuffer nobody composites, with no GL error
	 * and a completely healthy-looking engine -- instances, uniforms, bone matrices and linked programs
	 * all correct, and an empty screen.
	 *
	 * <p>The viewport goes with it: it is also per-pass state, so it is set to the target's own size
	 * rather than trusted. Both are restored, because vanilla's next pass reads the binding back through
	 * {@code GlStateManager}'s shadow.
	 */
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

	public static void onAfterLevel(RenderLevelStageEvent.AfterLevel event) {
		context = null;
	}

	private static void withManager(java.util.function.Consumer<VisualizationManager> action) {
		if (context == null) {
			return;
		}

		VisualizationManager manager = VisualizationManager.get(context.level());
		if (manager != null) {
			action.accept(manager);
		}
	}
}
