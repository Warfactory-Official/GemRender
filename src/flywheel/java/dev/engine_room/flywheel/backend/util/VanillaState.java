package dev.engine_room.flywheel.backend.util;

import org.lwjgl.opengl.GL32;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.textures.GpuTexture;

import net.minecraft.client.Minecraft;

/**
 * Vanilla state that 26.1 stopped exposing, recovered the same way vanilla computes it.
 * <p>
 * This is fork-only glue. When upstream Flywheel ships 26.1 support it will have its own answers for
 * all of this; see {@code src/flywheel/NOTICE.md}.
 */
public final class VanillaState {
	private VanillaState() {
	}

	/**
	 * The far plane distance.
	 * <p>
	 * 26.1 moved this off {@code GameRenderer#getDepthFar} and onto a private {@code Camera#depthFar},
	 * readable only through a {@code CameraRenderState} the backend is never handed. Both inputs are
	 * public though, so recompute it -- this is verbatim what {@code Camera#update} does.
	 */
	public static float depthFar() {
		var options = Minecraft.getInstance().options;
		float renderDistance = options.getEffectiveRenderDistance() * 16;
		return Math.max(renderDistance * 4.0F, (float) (options.cloudRange()
				.get() * 16));
	}

	/**
	 * The GL name behind a {@link GpuTexture}. Flywheel issues its own draws and attaches vanilla's
	 * textures to its own framebuffers, so it needs the handle rather than the view.
	 */
	public static int glId(GpuTexture texture) {
		return ((GlTexture) texture).glId();
	}

	private static int scratchFbo = -1;

	/**
	 * A framebuffer with this render target's color and depth textures attached.
	 * <p>
	 * 26.1 removed {@code RenderTarget#bindWrite}: there is no "current framebuffer" any more, every
	 * vanilla draw names its attachments through a render pass. Flywheel draws with its own programs,
	 * so it needs one, and keeps a single FBO it re-attaches rather than a cache -- vanilla's own
	 * per-texture FBO cache lives behind the package-private {@code GlDevice}, and a cache of our own
	 * keyed on GL names would go stale the moment the driver recycled one after a window resize.
	 */
	public static int fboOf(RenderTarget target) {
		return fboOf(target.getColorTexture(), target.getDepthTexture());
	}

	/**
	 * The same, for a pair of textures that are not a {@link RenderTarget}.
	 * <p>
	 * 26.1's offscreen sinks -- the GUI item atlas, a picture-in-picture render -- do not have one. They
	 * name their output as a pair of texture views on {@code RenderSystem}, and vanilla's own draws reach
	 * it by building a render pass from them. Anything drawing with its own programs needs a framebuffer
	 * instead, and it must be the same scratch one: two would fight over the attachments.
	 */
	public static int fboOf(GpuTexture color, GpuTexture depth) {
		if (scratchFbo == -1) {
			scratchFbo = GL32.glGenFramebuffers();
		}

		GlStateManager._glBindFramebuffer(GL32.GL_FRAMEBUFFER, scratchFbo);
		GL32.glFramebufferTexture(GL32.GL_FRAMEBUFFER, GL32.GL_COLOR_ATTACHMENT0, glId(color), 0);
		GL32.glFramebufferTexture(GL32.GL_FRAMEBUFFER, GL32.GL_DEPTH_ATTACHMENT, depth == null ? 0 : glId(depth), 0);
		return scratchFbo;
	}

	/**
	 * The render target Flywheel composites into: the item-entity target under Fabulous graphics,
	 * where translucency is composited later, and the main target otherwise.
	 */
	public static RenderTarget compositeTarget() {
		if (Minecraft.useShaderTransparency()) {
			RenderTarget itemEntityTarget = Minecraft.getInstance().levelRenderer.getItemEntityTarget();
			if (itemEntityTarget != null) {
				return itemEntityTarget;
			}
		}
		return Minecraft.getInstance()
				.getMainRenderTarget();
	}
}
