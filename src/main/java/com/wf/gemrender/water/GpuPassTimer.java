package com.wf.gemrender.water;

import org.lwjgl.opengl.GL15C;

import static org.lwjgl.opengl.GL33C.*;

public final class GpuPassTimer {
    private static final int RING = 4;
    /** One GL_TIME_ELAPSED query may be active per context; a nested begin => GL_INVALID_OPERATION per call. */
    private static GpuPassTimer running;

    private final int[] queries = new int[RING];
    private final boolean[] pending = new boolean[RING];
    private int cursor;
    private boolean created;

    private long gpuTotalNanos;
    private long gpuSamples;
    private long cpuTotalNanos;
    private long cpuSamples;
    private long cpuStart;
    private boolean started;

    /** Nested inside another running timer => no-op; the outer span covers it. */
    public void begin() {
        if (running != null) {
            return;
        }
        if (!created) {
            glGenQueries(queries);
            created = true;
        }

        harvest(cursor);
        glBeginQuery(GL_TIME_ELAPSED, queries[cursor]);
        cpuStart = System.nanoTime();
        running = this;
        started = true;
    }

    public void end() {
        if (!started) {
            return;
        }
        started = false;
        running = null;
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
        gpuTotalNanos += glGetQueryObjectui64(queries[slot], GL15C.GL_QUERY_RESULT);
        gpuSamples++;
        pending[slot] = false;
    }

    public void reset() {
        gpuTotalNanos = 0;
        gpuSamples = 0;
        cpuTotalNanos = 0;
        cpuSamples = 0;
    }

    public long meanGpuMicros() {
        return gpuSamples == 0 ? 0 : gpuTotalNanos / gpuSamples / 1000;
    }

    public long meanCpuMicros() {
        return cpuSamples == 0 ? 0 : cpuTotalNanos / cpuSamples / 1000;
    }

    public long meanGpuNanos() {
        return gpuSamples == 0 ? 0 : gpuTotalNanos / gpuSamples;
    }

    public long meanCpuNanos() {
        return cpuSamples == 0 ? 0 : cpuTotalNanos / cpuSamples;
    }

    public long samples() {
        return gpuSamples;
    }
}
