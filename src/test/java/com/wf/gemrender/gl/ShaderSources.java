package com.wf.gemrender.gl;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

public final class ShaderSources {
	private ShaderSources() {
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
