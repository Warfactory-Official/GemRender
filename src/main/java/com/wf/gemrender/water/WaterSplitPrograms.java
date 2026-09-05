package com.wf.gemrender.water;

import static org.lwjgl.opengl.GL20C.GL_COMPILE_STATUS;
import static org.lwjgl.opengl.GL20C.GL_FRAGMENT_SHADER;
import static org.lwjgl.opengl.GL20C.GL_LINK_STATUS;
import static org.lwjgl.opengl.GL20C.GL_VERTEX_SHADER;
import static org.lwjgl.opengl.GL20C.glAttachShader;
import static org.lwjgl.opengl.GL20C.glCompileShader;
import static org.lwjgl.opengl.GL20C.glCreateProgram;
import static org.lwjgl.opengl.GL20C.glCreateShader;
import static org.lwjgl.opengl.GL20C.glDeleteShader;
import static org.lwjgl.opengl.GL20C.glGetProgramInfoLog;
import static org.lwjgl.opengl.GL20C.glGetProgrami;
import static org.lwjgl.opengl.GL20C.glGetShaderInfoLog;
import static org.lwjgl.opengl.GL20C.glGetShaderi;
import static org.lwjgl.opengl.GL20C.glGetUniformLocation;
import static org.lwjgl.opengl.GL20C.glLinkProgram;
import static org.lwjgl.opengl.GL20C.glShaderSource;
import static org.lwjgl.opengl.GL20C.glUniform1f;
import static org.lwjgl.opengl.GL20C.glUniform1i;
import static org.lwjgl.opengl.GL20C.glUseProgram;
import static org.lwjgl.opengl.GL20C.GL_CURRENT_PROGRAM;
import static org.lwjgl.opengl.GL30C.GL_TEXTURE_2D_ARRAY;
import static org.lwjgl.opengl.GL30C.GL_VERTEX_ARRAY_BINDING;
import static org.lwjgl.opengl.GL30C.glGenVertexArrays;
import static org.lwjgl.opengl.GL33C.GL_TEXTURE0;
import static org.lwjgl.opengl.GL33C.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL33C.GL_TEXTURE_BINDING_2D;
import static org.lwjgl.opengl.GL33C.GL_TRIANGLES;
import static org.lwjgl.opengl.GL33C.glBindTexture;
import static org.lwjgl.opengl.GL33C.glDrawArrays;
import static org.lwjgl.opengl.GL33C.glGetInteger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;

import com.wf.gemrender.GemRender;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

final class WaterSplitPrograms {
	private static final String VERSION = "#version 420 core\n";

	private int depthCopyProgram;
	private int behindProgram;
	private int frontProgram;
	private int absorbanceCompositeProgram;
	private int absorbanceBehindProgram;
	private int absorbanceFrontProgram;
	private int vao;

	private int depthCopyDepthLoc;
	private int behindZNearLoc;
	private int behindZFarLoc;
	private int frontZNearLoc;
	private int frontZFarLoc;

	private boolean created;
	private boolean failed;

	private static final int UNIT_ACCUMULATE = 0;
	private static final int UNIT_FRONT = 1;
	private static final int UNIT_DEPTH_RANGE = 2;
	private static final int UNIT_COEFFICIENTS = 3;
	private static final int UNIT_WATER_DEPTH = 4;

	boolean ensureCreated() {
		if (created) {
			return true;
		}
		if (failed) {
			return false;
		}

		try {
			String wavelet = resource("flywheel", "flywheel/internal/wavelet.glsl");
			String depth = resource("flywheel", "flywheel/internal/depth.glsl");
			String vert = VERSION + resource(GemRender.MOD_ID, "shaders/water_split.vert");

			depthCopyProgram = link("depth_copy",
					vert, VERSION + resource(GemRender.MOD_ID, "shaders/depth_copy.frag"));
			behindProgram = link("water_behind",
					vert, VERSION + wavelet + depth + resource(GemRender.MOD_ID, "shaders/water_behind.frag"));
			frontProgram = link("water_front",
					vert, VERSION + wavelet + depth + resource(GemRender.MOD_ID, "shaders/water_front.frag"));

			depthCopyDepthLoc = glGetUniformLocation(depthCopyProgram, "_gr_depth");

			bindSamplers(behindProgram);
			behindZNearLoc = glGetUniformLocation(behindProgram, "_gr_znear");
			behindZFarLoc = glGetUniformLocation(behindProgram, "_gr_zfar");

			bindSamplers(frontProgram);
			frontZNearLoc = glGetUniformLocation(frontProgram, "_gr_znear");
			frontZFarLoc = glGetUniformLocation(frontProgram, "_gr_zfar");

			absorbanceCompositeProgram = link("absorbance_composite",
					vert, VERSION + resource(GemRender.MOD_ID, "shaders/absorbance_composite.frag"));
			absorbanceBehindProgram = link("absorbance_behind",
					vert, VERSION + resource(GemRender.MOD_ID, "shaders/absorbance_behind.frag"));
			absorbanceFrontProgram = link("absorbance_front",
					vert, VERSION + resource(GemRender.MOD_ID, "shaders/absorbance_front.frag"));

			bindSamplers(absorbanceCompositeProgram);
			bindSamplers(absorbanceBehindProgram);
			bindSamplers(absorbanceFrontProgram);

			glUseProgram(0);
			vao = glGenVertexArrays();
			created = true;
			return true;
		} catch (RuntimeException | IOException e) {
			failed = true;
			GemRender.LOGGER.error("Water split disabled: its shaders failed to build. Translucent models "
					+ "will occlude the water behind them, as they did before the split.", e);
			return false;
		}
	}

	private static void bindSamplers(int program) {
		glUseProgram(program);
		glUniform1i(glGetUniformLocation(program, "_gr_accumulate"), UNIT_ACCUMULATE);
		glUniform1i(glGetUniformLocation(program, "_gr_frontAccumulate"), UNIT_FRONT);
		glUniform1i(glGetUniformLocation(program, "_gr_depthRange"), UNIT_DEPTH_RANGE);
		glUniform1i(glGetUniformLocation(program, "_gr_coefficients"), UNIT_COEFFICIENTS);
		glUniform1i(glGetUniformLocation(program, "_gr_waterDepth"), UNIT_WATER_DEPTH);
	}

	void drawDepthCopy(int depthTexture) {
		int previousProgram = glGetInteger(GL_CURRENT_PROGRAM);
		try {
			glUseProgram(depthCopyProgram);
			glUniform1i(depthCopyDepthLoc, 0);
			bind2d(0, depthTexture);
			drawFullscreen();
			unbind(0);
		} finally {
			glUseProgram(previousProgram);
		}
	}

	void drawBehind(int accumulate, int front, int depthRange, int coefficients, int waterDepth) {
		int previousProgram = glGetInteger(GL_CURRENT_PROGRAM);
		try {
			glUseProgram(behindProgram);
			setZRange(behindZNearLoc, behindZFarLoc);
			bindCompositeTextures(accumulate, front, depthRange, coefficients, waterDepth);
			drawFullscreen();
			unbindCompositeTextures();
		} finally {
			glUseProgram(previousProgram);
		}
	}

	void drawFront(int accumulate, int front, int depthRange, int coefficients, int waterDepth) {
		int previousProgram = glGetInteger(GL_CURRENT_PROGRAM);
		try {
			glUseProgram(frontProgram);
			setZRange(frontZNearLoc, frontZFarLoc);
			bindCompositeTextures(accumulate, front, depthRange, coefficients, waterDepth);
			drawFullscreen();
			unbindCompositeTextures();
		} finally {
			glUseProgram(previousProgram);
		}
	}

	void drawAbsorbanceComposite(int accumulate) {
		drawAccumulators(absorbanceCompositeProgram, accumulate, 0);
	}

	void drawAbsorbanceBehind(int accumulate, int front) {
		drawAccumulators(absorbanceBehindProgram, accumulate, front);
	}

	void drawAbsorbanceFront(int front) {
		drawAccumulators(absorbanceFrontProgram, 0, front);
	}

	private void drawAccumulators(int program, int accumulate, int front) {
		int previousProgram = glGetInteger(GL_CURRENT_PROGRAM);
		try {
			glUseProgram(program);
			bind2d(UNIT_ACCUMULATE, accumulate);
			bind2d(UNIT_FRONT, front);
			GlStateManager._activeTexture(GL_TEXTURE0);
			drawFullscreen();
			unbind(UNIT_FRONT);
			unbind(UNIT_ACCUMULATE);
			GlStateManager._activeTexture(GL_TEXTURE0);
		} finally {
			glUseProgram(previousProgram);
		}
	}

	private static void setZRange(int znearLoc, int zfarLoc) {
		glUniform1f(znearLoc, net.minecraft.client.renderer.GameRenderer.PROJECTION_Z_NEAR);
		glUniform1f(zfarLoc, Minecraft.getInstance().gameRenderer.getDepthFar());
	}

	/**
	 * What each unit held before this pass borrowed it. Units 0-2 are Minecraft's block atlas, lightmap
	 * and overlay; unbinding them to zero instead of putting them back is a leak that vanilla happens to
	 * recover from, because {@code _bindTexture} tracks the zero and so reissues the bind it would
	 * otherwise skip. Nothing using raw GL recovers from it.
	 */
	private final int[] borrowed = new int[UNIT_WATER_DEPTH + 1];

	private void bindCompositeTextures(int accumulate, int front, int depthRange, int coefficients,
			int waterDepth) {
		bind2d(UNIT_ACCUMULATE, accumulate);
		bind2d(UNIT_FRONT, front);
		bind2d(UNIT_DEPTH_RANGE, depthRange);

		GlStateManager._activeTexture(GL_TEXTURE0 + UNIT_COEFFICIENTS);
		borrowed[UNIT_COEFFICIENTS] = glGetInteger(GL_TEXTURE_BINDING_2D);
		RenderSystem.bindTexture(0);
		glBindTexture(GL_TEXTURE_2D_ARRAY, coefficients);
		bind2d(UNIT_WATER_DEPTH, waterDepth);
		GlStateManager._activeTexture(GL_TEXTURE0);
	}

	private void unbindCompositeTextures() {
		for (int unit = UNIT_WATER_DEPTH; unit >= 0; unit--) {
			if (unit == UNIT_COEFFICIENTS) {
				GlStateManager._activeTexture(GL_TEXTURE0 + unit);
				glBindTexture(GL_TEXTURE_2D_ARRAY, 0);
				GlStateManager._bindTexture(borrowed[unit]);
			} else {
				unbind(unit);
			}
		}
		GlStateManager._activeTexture(GL_TEXTURE0);
	}

	private void bind2d(int unit, int texture) {
		GlStateManager._activeTexture(GL_TEXTURE0 + unit);
		borrowed[unit] = glGetInteger(GL_TEXTURE_BINDING_2D);
		GlStateManager._bindTexture(texture);
	}

	private void unbind(int unit) {
		GlStateManager._activeTexture(GL_TEXTURE0 + unit);
		GlStateManager._bindTexture(borrowed[unit]);
	}

	/**
	 * Binds our vertex array for the one triangle, then puts back the caller's. Minecraft keeps no shadow
	 * copy of the vertex array binding, so a leak here is not corrected by anything downstream: the next
	 * draw that assumes its own array is still bound silently reads our attributes instead.
	 */
	private void drawFullscreen() {
		int previousArray = glGetInteger(GL_VERTEX_ARRAY_BINDING);
		try {
			GlStateManager._glBindVertexArray(vao);
			glDrawArrays(GL_TRIANGLES, 0, 3);
		} finally {
			GlStateManager._glBindVertexArray(previousArray);
		}
	}

	private static String resource(String namespace, String path) throws IOException {
		ResourceLocation location = ResourceLocation.fromNamespaceAndPath(namespace, path);
		try (InputStream in = Minecraft.getInstance()
				.getResourceManager()
				.open(location)) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private static int link(String name, String vertexSource, String fragmentSource) {
		int vertex = compile(name, GL_VERTEX_SHADER, vertexSource);
		int fragment = compile(name, GL_FRAGMENT_SHADER, fragmentSource);

		int program = glCreateProgram();
		glAttachShader(program, vertex);
		glAttachShader(program, fragment);
		glLinkProgram(program);
		glDeleteShader(vertex);
		glDeleteShader(fragment);

		if (glGetProgrami(program, GL_LINK_STATUS) == 0) {
			throw new IllegalStateException(name + " failed to link: " + glGetProgramInfoLog(program));
		}
		return program;
	}

	private static int compile(String name, int type, String source) {
		int shader = glCreateShader(type);
		glShaderSource(shader, source);
		glCompileShader(shader);
		if (glGetShaderi(shader, GL_COMPILE_STATUS) == 0) {
			throw new IllegalStateException(name + (type == GL_VERTEX_SHADER ? " (vert)" : " (frag)")
					+ " failed to compile: " + glGetShaderInfoLog(shader));
		}
		return shader;
	}
}
