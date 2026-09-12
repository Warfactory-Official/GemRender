package dev.engine_room.flywheel.backend.engine;

import java.util.Comparator;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.opengl.GlConst;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;

import dev.engine_room.flywheel.api.material.DepthTest;
import dev.engine_room.flywheel.api.material.Material;
import dev.engine_room.flywheel.api.material.Transparency;
import dev.engine_room.flywheel.api.material.WriteMask;
import dev.engine_room.flywheel.backend.Samplers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;

/**
 * 26.1 removed the imperative state setters from {@code RenderSystem} -- blending, culling, depth and
 * the write masks now live inside {@link com.mojang.blaze3d.pipeline.RenderPipeline} objects that
 * vanilla applies per render pass. Flywheel is its own GL backend and draws outside that system, so it
 * sets the state itself.
 * <p>
 * Every call below goes through {@link GlStateManager} rather than raw LWJGL on purpose: GlStateManager
 * still keeps vanilla's shadow copy of the GL state, and vanilla's pipeline application short-circuits
 * against that shadow. Going around it would leave the shadow lying about the driver.
 */
public final class MaterialRenderState {
	public static final Comparator<Material> COMPARATOR = MaterialRenderState::compare;

	private MaterialRenderState() {
	}

	public static void setup(Material material) {
		setupTexture(material);
		setupBackfaceCulling(material.backfaceCulling());
		setupPolygonOffset(material.polygonOffset());
		setupDepthTest(material.depthTest());
		setupTransparency(material.transparency());
		setupWriteMask(material.writeMask());
	}

	public static void setupOit(Material material) {
		setupTexture(material);
		setupBackfaceCulling(material.backfaceCulling());
		setupPolygonOffset(material.polygonOffset());
		setupDepthTest(material.depthTest());

		WriteMask mask = material.writeMask();
		colorMask(mask.color());
	}

	private static void setupTexture(Material material) {
		AbstractTexture texture = Minecraft.getInstance()
				.getTextureManager()
				.getTexture(material.texture());
		TextureBinder.bind(Samplers.DIFFUSE, texture, material.blur(), material.mipmap());
	}

	private static void setupBackfaceCulling(boolean backfaceCulling) {
		if (backfaceCulling) {
			GlStateManager._enableCull();
		} else {
			GlStateManager._disableCull();
		}
	}

	private static void setupPolygonOffset(boolean polygonOffset) {
		if (polygonOffset) {
			GlStateManager._polygonOffset(-1.0F, -10.0F);
			GlStateManager._enablePolygonOffset();
		} else {
			GlStateManager._polygonOffset(0.0F, 0.0F);
			GlStateManager._disablePolygonOffset();
		}
	}

	private static void setupDepthTest(DepthTest depthTest) {
		switch (depthTest) {
		case OFF -> {
			GlStateManager._disableDepthTest();
		}
		case NEVER -> {
			GlStateManager._enableDepthTest();
			GlStateManager._depthFunc(GL_NEVER);
		}
		case LESS -> {
			GlStateManager._enableDepthTest();
			GlStateManager._depthFunc(GlConst.GL_LESS);
		}
		case EQUAL -> {
			GlStateManager._enableDepthTest();
			GlStateManager._depthFunc(GlConst.GL_EQUAL);
		}
		case LEQUAL -> {
			GlStateManager._enableDepthTest();
			GlStateManager._depthFunc(GlConst.GL_LEQUAL);
		}
		case GREATER -> {
			GlStateManager._enableDepthTest();
			GlStateManager._depthFunc(GlConst.GL_GREATER);
		}
		case NOTEQUAL -> {
			GlStateManager._enableDepthTest();
			GlStateManager._depthFunc(GL_NOTEQUAL);
		}
		case GEQUAL -> {
			GlStateManager._enableDepthTest();
			GlStateManager._depthFunc(GlConst.GL_GEQUAL);
		}
		case ALWAYS -> {
			GlStateManager._enableDepthTest();
			GlStateManager._depthFunc(GlConst.GL_ALWAYS);
		}
		}
	}

	private static void setupTransparency(Transparency transparency) {
		switch (transparency) {
		case OPAQUE -> {
			GlStateManager._disableBlend();
		}
		case ADDITIVE -> {
			GlStateManager._enableBlend();
			blendFunc(SourceFactor.ONE, DestFactor.ONE);
		}
		case LIGHTNING -> {
			GlStateManager._enableBlend();
			blendFunc(SourceFactor.SRC_ALPHA, DestFactor.ONE);
		}
		case GLINT -> {
			GlStateManager._enableBlend();
			blendFuncSeparate(SourceFactor.SRC_COLOR, DestFactor.ONE, SourceFactor.ZERO, DestFactor.ONE);
		}
		case CRUMBLING -> {
			GlStateManager._enableBlend();
			blendFuncSeparate(SourceFactor.DST_COLOR, DestFactor.SRC_COLOR, SourceFactor.ONE, DestFactor.ZERO);
		}
		case TRANSLUCENT -> {
			GlStateManager._enableBlend();
			blendFuncSeparate(SourceFactor.SRC_ALPHA, DestFactor.ONE_MINUS_SRC_ALPHA, SourceFactor.ONE, DestFactor.ONE_MINUS_SRC_ALPHA);
		}
		}
	}

	private static void setupWriteMask(WriteMask mask) {
		GlStateManager._depthMask(mask.depth());
		colorMask(mask.color());
	}

	public static void reset() {
		resetTexture();
		resetBackfaceCulling();
		resetPolygonOffset();
		resetDepthTest();
		resetTransparency();
		resetWriteMask();
	}

	private static void resetTexture() {
		TextureBinder.unbind(Samplers.DIFFUSE);
	}

	private static void resetBackfaceCulling() {
		GlStateManager._enableCull();
	}

	private static void resetPolygonOffset() {
		GlStateManager._polygonOffset(0.0F, 0.0F);
		GlStateManager._disablePolygonOffset();
	}

	private static void resetDepthTest() {
		GlStateManager._disableDepthTest();
		GlStateManager._depthFunc(GlConst.GL_LEQUAL);
	}

	private static void resetTransparency() {
		GlStateManager._disableBlend();
		// RenderSystem.defaultBlendFunc() is gone in 26.1; this is what it did.
		blendFuncSeparate(SourceFactor.SRC_ALPHA, DestFactor.ONE_MINUS_SRC_ALPHA, SourceFactor.ONE, DestFactor.ZERO);
	}

	private static void resetWriteMask() {
		GlStateManager._depthMask(true);
		colorMask(true);
	}

	// GlConst does not name every compare function Flywheel offers.
	private static final int GL_NEVER = 0x0200;
	private static final int GL_NOTEQUAL = 0x0205;

	/**
	 * 26.1 folded the four booleans of {@code glColorMask} into one bitmask, spelled by
	 * {@link ColorTargetState}.
	 */
	private static void colorMask(boolean writeColor) {
		GlStateManager._colorMask(writeColor ? ColorTargetState.WRITE_ALL : ColorTargetState.WRITE_NONE);
	}

	private static void blendFunc(SourceFactor srcFactor, DestFactor dstFactor) {
		blendFuncSeparate(srcFactor, dstFactor, srcFactor, dstFactor);
	}

	private static void blendFuncSeparate(SourceFactor srcColor, DestFactor dstColor, SourceFactor srcAlpha, DestFactor dstAlpha) {
		GlStateManager._blendFuncSeparate(GlConst.toGl(srcColor), GlConst.toGl(dstColor), GlConst.toGl(srcAlpha), GlConst.toGl(dstAlpha));
	}

	public static boolean materialEquals(Material lhs, Material rhs) {
		if (lhs == rhs) {
			return true;
		}

		// Not here because ubershader: useLight, useOverlay, diffuse, fog shader, ambient occlusion
		// Everything in the comparator should be here.
		// @formatter:off
		return lhs.blur() == rhs.blur()
				&& lhs.mipmap() == rhs.mipmap()
				&& lhs.backfaceCulling() == rhs.backfaceCulling()
				&& lhs.polygonOffset() == rhs.polygonOffset()
				&& lhs.depthTest() == rhs.depthTest()
				&& lhs.transparency() == rhs.transparency()
				&& lhs.writeMask() == rhs.writeMask()
				&& lhs.light().source().equals(rhs.light().source())
				&& lhs.texture().equals(rhs.texture())
				&& lhs.cutout().source().equals(rhs.cutout().source())
				&& lhs.shaders().fragmentSource().equals(rhs.shaders().fragmentSource())
				&& lhs.shaders().vertexSource().equals(rhs.shaders().vertexSource());
		// @formatter:on
	}

	public static boolean materialIsAllNonNull(@Nullable Material material) {
		// We do not trust people to give us valid NotNull objects.
		// @formatter:off
		return material != null &&
				material.shaders() != null &&
				material.shaders().fragmentSource() != null &&
				material.shaders().vertexSource() != null &&
				material.fog() != null &&
				material.fog().source() != null &&
				material.cutout() != null &&
				material.cutout().source() != null &&
				material.light() != null &&
				material.light().source() != null &&
				material.texture() != null &&
				material.depthTest() != null &&
				material.transparency() != null &&
				material.writeMask() != null &&
				material.cardinalLightingMode() != null;
		// @formatter:on
	}

	public static int compare(Material lhs, Material rhs) {
		if (lhs == rhs) {
			return 0;
		}

		int cmp;
		cmp = lhs.transparency()
				.compareTo(rhs.transparency());
		if (cmp != 0) {
			return cmp;
		}
		cmp = lhs.light()
				.source()
				.compareTo(rhs.light()
						.source());
		if (cmp != 0) {
			return cmp;
		}
		cmp = lhs.cutout()
				.source()
				.compareTo(rhs.cutout()
						.source());
		if (cmp != 0) {
			return cmp;
		}
		cmp = lhs.shaders()
				.fragmentSource()
				.compareTo(rhs.shaders()
						.fragmentSource());
		if (cmp != 0) {
			return cmp;
		}
		cmp = lhs.shaders()
				.vertexSource()
				.compareTo(rhs.shaders()
						.vertexSource());
		if (cmp != 0) {
			return cmp;
		}
		cmp = lhs.texture()
				.compareTo(rhs.texture());
		if (cmp != 0) {
			return cmp;
		}
		cmp = Boolean.compare(lhs.blur(), rhs.blur());
		if (cmp != 0) {
			return cmp;
		}
		cmp = Boolean.compare(lhs.mipmap(), rhs.mipmap());
		if (cmp != 0) {
			return cmp;
		}
		cmp = Boolean.compare(lhs.backfaceCulling(), rhs.backfaceCulling());
		if (cmp != 0) {
			return cmp;
		}
		cmp = Boolean.compare(lhs.polygonOffset(), rhs.polygonOffset());
		if (cmp != 0) {
			return cmp;
		}
		cmp = lhs.depthTest()
				.compareTo(rhs.depthTest());
		if (cmp != 0) {
			return cmp;
		}
		cmp = lhs.writeMask()
				.compareTo(rhs.writeMask());
		if (cmp != 0) {
			return cmp;
		}
		return 0;
	}
}
