package com.wf.gemrender.water;

import static org.lwjgl.opengl.GL33C.GL_QUERY_RESULT_AVAILABLE;
import static org.lwjgl.opengl.GL33C.GL_TIME_ELAPSED;
import static org.lwjgl.opengl.GL33C.glBeginQuery;
import static org.lwjgl.opengl.GL33C.glEndQuery;
import static org.lwjgl.opengl.GL33C.glGenQueries;
import static org.lwjgl.opengl.GL33C.glGetQueryObjecti;
import static org.lwjgl.opengl.GL33C.glGetQueryObjectui64;

final class GpuPassTimer {
	private static final int RING = 4;

	private final int[] queries = new int[RING];
	private final boolean[] pending = new boolean[RING];
	private int cursor;
	private boolean created;

	private long gpuTotalNanos;
	private long gpuSamples;
	private long cpuTotalNanos;
	private long cpuSamples;
	private long cpuStart;

	void begin() {
		if (!created) {
			glGenQueries(queries);
			created = true;
		}

		harvest(cursor);
		glBeginQuery(GL_TIME_ELAPSED, queries[cursor]);
		cpuStart = System.nanoTime();
	}

	void end() {
		cpuTotalNanos += System.nanoTime() - cpuStart;
		cpuSamples++;
		glEndQuery(GL_TIME_ELAPSED);
		pending[cursor] = true;
		cursor = (cursor + 1) % RING;
	}

	private void harvest(int slot) {
		if (!pending[slot]) {
			return;
		}
		if (glGetQueryObjecti(queries[slot], GL_QUERY_RESULT_AVAILABLE) == 0) {
			return;
		}
		gpuTotalNanos += glGetQueryObjectui64(queries[slot], org.lwjgl.opengl.GL15C.GL_QUERY_RESULT);
		gpuSamples++;
		pending[slot] = false;
	}

	void reset() {
		gpuTotalNanos = 0;
		gpuSamples = 0;
		cpuTotalNanos = 0;
		cpuSamples = 0;
	}

	long meanGpuMicros() {
		return gpuSamples == 0 ? 0 : gpuTotalNanos / gpuSamples / 1000;
	}

	long meanCpuMicros() {
		return cpuSamples == 0 ? 0 : cpuTotalNanos / cpuSamples / 1000;
	}

	long samples() {
		return gpuSamples;
	}
}
