package com.wf.gemrender.render;

import static org.lwjgl.opengl.GL33C.GL_TEXTURE0;

import org.lwjgl.opengl.GL13C;

import com.mojang.blaze3d.platform.GlStateManager;

/**
 * The three texture units GemRender's palette buffers live on, and the only supported way to reach them.
 *
 * <p>Both are above {@link GlStateManager#TEXTURE_COUNT}, which is deliberate – Minecraft tracks only
 * the units below it, so a unit above one cannot be clobbered by vanilla rendering. It also means
 * {@code GlStateManager._activeTexture} must never be told about them: it would write the unit index
 * into its shadow, and the next {@code _bindTexture} would index its 12-entry table out of bounds. So
 * the switch is a raw GL call, and {@link #restore} puts back the unit the caller had rather than
 * assuming unit 0 – leaving GL and Minecraft's shadow copy agreeing, which is the condition every later
 * {@code _bindTexture} silently depends on.
 */
public final class TextureUnits {
	public static final int BONES = Integer.getInteger("gemrender.boneunit", 14);

	public static final int MORPHS = Integer.getInteger("gemrender.morphunit", 15);

	public static final int PARTICLES = Integer.getInteger("gemrender.particleunit", 13);

	private TextureUnits() {
	}

	/**
	 * Makes {@code unit} active and returns the token to hand {@link #restore}. Always paired, always in
	 * a finally.
	 */
	public static int activate(int unit) {
		int previous = GlStateManager._getActiveTexture();
		GL13C.glActiveTexture(GL_TEXTURE0 + unit);
		return previous;
	}

	public static void restore(int previous) {
		GL13C.glActiveTexture(previous);
	}
}
