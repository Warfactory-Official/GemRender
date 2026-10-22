package com.wf.gemrender.gl;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

public final class ShaderSources {
	private ShaderSources() {
	}

	private static final String FLYWHEEL = "assets/gemrender/flywheel/";

	private static final String INCLUDE = "#include \"gemrender:";

	/** {@code particle.glsl}: {@link #inlined}. */
	public static String particleGlsl() {
		return inlined("particle.glsl");
	}

	/**
	 * {@code assets/gemrender/flywheel/<path>} with its {@code gemrender:} includes inlined, behind stand-ins for
	 * the Flywheel frame uniforms they read.
	 */
	public static String inlined(String path) {
		return """
				uniform mat4 flw_view;
				uniform mat4 flw_projection;
				uniform vec2 flw_viewportSize;
				""" + resolve(path);
	}

	private static String resolve(String path) {
		StringBuilder out = new StringBuilder();
		read(FLYWHEEL + path).lines().forEach(line -> {
			String trimmed = line.strip();
			out.append(trimmed.startsWith(INCLUDE)
					? resolve(trimmed.substring(INCLUDE.length(), trimmed.length() - 1)) : line).append('\n');
		});
		return out.toString();
	}

	public static String read(String resourcePath) {
		try (InputStream in = ShaderSources.class.getClassLoader().getResourceAsStream(resourcePath)) {
			if (in == null) {
				throw new AssertionError("Shader not found on the test classpath: " + resourcePath);
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
