package com.wf.gemrender.volume;

import static org.lwjgl.opengl.GL11C.GL_FLOAT;
import static org.lwjgl.opengl.GL11C.GL_NEAREST;
import static org.lwjgl.opengl.GL11C.GL_RED;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL11C.GL_VIEWPORT;
import static org.lwjgl.opengl.GL11C.glDeleteTextures;
import static org.lwjgl.opengl.GL11C.glGenTextures;
import static org.lwjgl.opengl.GL11C.glGetIntegerv;
import static org.lwjgl.opengl.GL11C.glTexParameteri;
import static org.lwjgl.opengl.GL12C.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL20C.GL_COMPILE_STATUS;
import static org.lwjgl.opengl.GL20C.GL_CURRENT_PROGRAM;
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
import static org.lwjgl.opengl.GL30C.GL_COLOR_ATTACHMENT0;
import static org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30C.GL_R32F;
import static org.lwjgl.opengl.GL30C.GL_VERTEX_ARRAY_BINDING;
import static org.lwjgl.opengl.GL30C.glDeleteFramebuffers;
import static org.lwjgl.opengl.GL30C.glGenFramebuffers;
import static org.lwjgl.opengl.GL30C.glGenVertexArrays;
import static org.lwjgl.opengl.GL32C.glFramebufferTexture;
import static org.lwjgl.opengl.GL33C.GL_TEXTURE0;
import static org.lwjgl.opengl.GL33C.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL33C.GL_TEXTURE_BINDING_2D;
import static org.lwjgl.opengl.GL33C.GL_TRIANGLES;
import static org.lwjgl.opengl.GL33C.glDrawArrays;
import static org.lwjgl.opengl.GL33C.glGetInteger;
import static org.lwjgl.opengl.GL33C.glTexImage2D;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.lwjgl.opengl.GL11C;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;

import com.wf.gemrender.GemRender;
import com.wf.gemrender.render.GlAudit;
import com.wf.gemrender.render.TextureUnits;
import com.wf.gemrender.water.PassState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;

public final class SceneDepth {
	public static final int TEXTURE_UNIT = TextureUnits.SCENE_DEPTH;

	private static final String VERSION = "#version 420 core\n";

	private static final SceneDepth INSTANCE = new SceneDepth();

	private final PassState state = new PassState();

	private final int[] viewport = new int[4];

	private int program;

	private int vao;

	private int depthLoc;

	private int znearLoc;

	private int zfarLoc;

	private boolean created;

	private boolean failed;

	private int fbo;

	private int texture;

	private int width = -1;

	private int height = -1;

	private SceneDepth() {
	}

	public static SceneDepth getInstance() {
		return INSTANCE;
	}

	public int textureId() {
		return texture;
	}

	public void bind() {
		if (texture == 0) {
			return;
		}

		int previousUnit = TextureUnits.activate(TEXTURE_UNIT);
		try {
			GL11C.glBindTexture(GL_TEXTURE_2D, texture);
		} finally {
			TextureUnits.restore(previousUnit);
		}
	}

	public void capture() {
		if (!ensureCreated()) {
			return;
		}

		Minecraft mc = Minecraft.getInstance();
		RenderTarget main = mc.getMainRenderTarget();
		ensureSize(main.width, main.height);

		GlAudit.Scope audit = GlAudit.open("volume:depth");
		state.save();
		glGetIntegerv(GL_VIEWPORT, viewport);

		try {
			GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, fbo);
			GlStateManager._viewport(0, 0, width, height);

			RenderSystem.disableBlend();
			RenderSystem.disableDepthTest();

			int previousProgram = glGetInteger(GL_CURRENT_PROGRAM);
			try {
				glUseProgram(program);
				glUniform1f(znearLoc, GameRenderer.PROJECTION_Z_NEAR);
				glUniform1f(zfarLoc, mc.gameRenderer.getDepthFar());
				glUniform1i(depthLoc, 0);

				GlStateManager._activeTexture(GL_TEXTURE0);
				int borrowed = glGetInteger(GL_TEXTURE_BINDING_2D);
				try {
					GlStateManager._bindTexture(main.getDepthTextureId());
					drawFullscreen();
				} finally {
					GlStateManager._bindTexture(borrowed);
				}
			} finally {
				glUseProgram(previousProgram);
			}
		} finally {
			GlStateManager._viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
			state.restore();
			audit.close();
		}

		bind();
	}

	private boolean ensureCreated() {
		if (created) {
			return true;
		}
		if (failed) {
			return false;
		}

		try {
			program = link("scene_depth", VERSION + resource("shaders/water_split.vert"),
					VERSION + resource("shaders/scene_depth.frag"));

			depthLoc = glGetUniformLocation(program, "_gr_depth");
			znearLoc = glGetUniformLocation(program, "_gr_znear");
			zfarLoc = glGetUniformLocation(program, "_gr_zfar");

			vao = glGenVertexArrays();
			created = true;
			return true;
		} catch (RuntimeException | IOException e) {
			failed = true;
			GemRender.LOGGER.error("Volumetrics disabled: the scene depth copy failed to build. Raymarched "
					+ "volumes would draw through solid geometry without it.", e);
			return false;
		}
	}

	private void ensureSize(int newWidth, int newHeight) {
		if (width == newWidth && height == newHeight) {
			return;
		}
		width = newWidth;
		height = newHeight;

		if (texture != 0) {
			glDeleteTextures(texture);
			glDeleteFramebuffers(fbo);
		}

		texture = glGenTextures();

		int previousTexture = glGetInteger(GL_TEXTURE_BINDING_2D);
		try {
			GlStateManager._bindTexture(texture);
			glTexImage2D(GL_TEXTURE_2D, 0, GL_R32F, width, height, 0, GL_RED, GL_FLOAT,
					(java.nio.ByteBuffer) null);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
		} finally {
			GlStateManager._bindTexture(previousTexture);
		}

		int previousFramebuffer = glGetInteger(org.lwjgl.opengl.GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
		fbo = glGenFramebuffers();
		GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, fbo);
		glFramebufferTexture(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, texture, 0);
		GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, previousFramebuffer);
	}

	private void drawFullscreen() {
		int previousArray = glGetInteger(GL_VERTEX_ARRAY_BINDING);
		try {
			GlStateManager._glBindVertexArray(vao);
			glDrawArrays(GL_TRIANGLES, 0, 3);
		} finally {
			GlStateManager._glBindVertexArray(previousArray);
		}
	}

	private static String resource(String path) throws IOException {
		ResourceLocation location = ResourceLocation.fromNamespaceAndPath(GemRender.MOD_ID, path);
		try (InputStream in = Minecraft.getInstance()
				.getResourceManager()
				.open(location)) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private static int link(String name, String vertexSource, String fragmentSource) {
		int vertex = compile(name, GL_VERTEX_SHADER, vertexSource);
		int fragment = compile(name, GL_FRAGMENT_SHADER, fragmentSource);

		int id = glCreateProgram();
		glAttachShader(id, vertex);
		glAttachShader(id, fragment);
		glLinkProgram(id);
		glDeleteShader(vertex);
		glDeleteShader(fragment);

		if (glGetProgrami(id, GL_LINK_STATUS) == 0) {
			throw new IllegalStateException(name + " failed to link: " + glGetProgramInfoLog(id));
		}
		return id;
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
