package com.wf.gemrender.gl;

import static org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MAJOR;
import static org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MINOR;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_CORE_PROFILE;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_PROFILE;
import static org.lwjgl.glfw.GLFW.GLFW_VISIBLE;
import static org.lwjgl.glfw.GLFW.glfwCreateWindow;
import static org.lwjgl.glfw.GLFW.glfwDestroyWindow;
import static org.lwjgl.glfw.GLFW.glfwInit;
import static org.lwjgl.glfw.GLFW.glfwMakeContextCurrent;
import static org.lwjgl.glfw.GLFW.glfwTerminate;
import static org.lwjgl.glfw.GLFW.glfwWindowHint;
import static org.lwjgl.opengl.GL46C.GL_COMPILE_STATUS;
import static org.lwjgl.opengl.GL46C.GL_COMPUTE_SHADER;
import static org.lwjgl.opengl.GL46C.GL_LINK_STATUS;
import static org.lwjgl.opengl.GL46C.GL_RENDERER;
import static org.lwjgl.opengl.GL46C.GL_TRUE;
import static org.lwjgl.opengl.GL46C.GL_VERSION;
import static org.lwjgl.opengl.GL46C.glAttachShader;
import static org.lwjgl.opengl.GL46C.glCompileShader;
import static org.lwjgl.opengl.GL46C.glCreateProgram;
import static org.lwjgl.opengl.GL46C.glCreateShader;
import static org.lwjgl.opengl.GL46C.glDeleteShader;
import static org.lwjgl.opengl.GL46C.glGetProgramInfoLog;
import static org.lwjgl.opengl.GL46C.glGetProgrami;
import static org.lwjgl.opengl.GL46C.glGetShaderInfoLog;
import static org.lwjgl.opengl.GL46C.glGetShaderi;
import static org.lwjgl.opengl.GL46C.glGetString;
import static org.lwjgl.opengl.GL46C.glGetUniformLocation;
import static org.lwjgl.opengl.GL46C.glLinkProgram;
import static org.lwjgl.opengl.GL46C.glShaderSource;
import static org.lwjgl.opengl.GL46C.glUniform1i;

import org.junit.jupiter.api.Assumptions;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;

public final class HeadlessGl implements AutoCloseable {
	private final long window;

	private HeadlessGl(long window) {
		this.window = window;
	}

	public static HeadlessGl createOrSkip() {
		Assumptions.assumeTrue(glfwInit(), "GLFW could not initialise; no display or no GPU");

		glfwWindowHint(GLFW_VISIBLE, org.lwjgl.glfw.GLFW.GLFW_FALSE);
		glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
		glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 6);
		glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);

		long window = glfwCreateWindow(1, 1, "gemrender-test", MemoryUtil.NULL, MemoryUtil.NULL);
		if (window == MemoryUtil.NULL) {
			glfwTerminate();
			Assumptions.abort("No OpenGL 4.6 core context available on this machine");
		}

		glfwMakeContextCurrent(window);
		GL.createCapabilities();

		System.out.println("[gemrender] GL context: " + glGetString(GL_RENDERER) + " / " + glGetString(GL_VERSION));
		return new HeadlessGl(window);
	}

	public int computeProgram(String source) {
		int shader = glCreateShader(GL_COMPUTE_SHADER);
		glShaderSource(shader, source);
		glCompileShader(shader);

		if (glGetShaderi(shader, GL_COMPILE_STATUS) != GL_TRUE) {
			String log = glGetShaderInfoLog(shader);
			glDeleteShader(shader);
			throw new AssertionError("Compute shader failed to compile:\n" + log + "\n--- source ---\n"
					+ numberLines(source));
		}

		int program = glCreateProgram();
		glAttachShader(program, shader);
		glLinkProgram(program);
		glDeleteShader(shader);

		if (glGetProgrami(program, GL_LINK_STATUS) != GL_TRUE) {
			throw new AssertionError("Compute program failed to link:\n" + glGetProgramInfoLog(program));
		}
		return program;
	}

	private static String numberLines(String source) {
		String[] lines = source.split("\n", -1);
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < lines.length; i++) {
			sb.append(String.format("%4d | %s%n", i + 1, lines[i]));
		}
		return sb.toString();
	}

	public static void samplerUnit(int program, String name, int unit) {
		glUniform1i(glGetUniformLocation(program, name), unit);
	}

	@Override
	public void close() {
		glfwMakeContextCurrent(MemoryUtil.NULL);
		glfwDestroyWindow(window);
		glfwTerminate();
	}
}
