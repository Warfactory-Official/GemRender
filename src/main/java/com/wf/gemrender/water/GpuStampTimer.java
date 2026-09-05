package com.wf.gemrender.water;

import static org.lwjgl.opengl.GL15C.GL_QUERY_RESULT;
import static org.lwjgl.opengl.GL33C.GL_QUERY_RESULT_AVAILABLE;
import static org.lwjgl.opengl.GL33C.GL_TIMESTAMP;
import static org.lwjgl.opengl.GL33C.glGenQueries;
import static org.lwjgl.opengl.GL33C.glGetQueryObjecti;
import static org.lwjgl.opengl.GL33C.glGetQueryObjectui64;
import static org.lwjgl.opengl.GL33C.glQueryCounter;

public final class GpuStampTimer {
	private static final int RING = 4;

	private final int[] starts = new int[RING];
	private final int[] ends = new int[RING];
	private final boolean[] pending = new boolean[RING];

	private int cursor;
	private boolean created;
	private boolean open;

	private long totalNanos;
	private long samples;

	public void begin() {
		if (!created) {
			glGenQueries(starts);
			glGenQueries(ends);
			created = true;
		}

		harvest(cursor);
		glQueryCounter(starts[cursor], GL_TIMESTAMP);
		open = true;
	}

	public void end() {
		if (!open) {
			return;
		}
		open = false;

		glQueryCounter(ends[cursor], GL_TIMESTAMP);
		pending[cursor] = true;
		cursor = (cursor + 1) % RING;
	}

	private void harvest(int slot) {
		if (!pending[slot]) {
			return;
		}
		if (glGetQueryObjecti(ends[slot], GL_QUERY_RESULT_AVAILABLE) == 0) {
			return;
		}

		totalNanos += glGetQueryObjectui64(ends[slot], GL_QUERY_RESULT)
				- glGetQueryObjectui64(starts[slot], GL_QUERY_RESULT);
		samples++;
		pending[slot] = false;
	}

	public void reset() {
		totalNanos = 0;
		samples = 0;
	}

	public long meanMicros() {
		return samples == 0 ? 0 : totalNanos / samples / 1000;
	}
}
