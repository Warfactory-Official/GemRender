package com.wf.gemrender.bench;

import org.joml.Matrix4f;

public interface BenchDriver {
	String name();

	boolean load();

	void render(Matrix4f modelView, Matrix4f pose, float animationSeconds);

	default void close() {
	}
}
