package com.wf.gemrender.bench;

import java.util.Arrays;

public final class BenchFrameTimer {
	private static final BenchFrameTimer INSTANCE = new BenchFrameTimer();

	private long[] frames = new long[4096];
	private int count;
	private long frameStartNanos;

	private BenchFrameTimer() {
	}

	public static BenchFrameTimer getInstance() {
		return INSTANCE;
	}

	public void frameStart() {
		long now = System.nanoTime();
		if (frameStartNanos != 0L) {
			record(now - frameStartNanos);
		}
		frameStartNanos = now;
	}

	public void frameEnd() {
	}

	private void record(long nanos) {
		if (count == frames.length) {
			frames = Arrays.copyOf(frames, frames.length * 2);
		}
		frames[count++] = nanos;
	}

	public void resetRun() {
		count = 0;
	}

	public int sampledFrames() {
		return count;
	}

	public long meanMicros() {
		if (count == 0) {
			return 0L;
		}
		long total = 0L;
		for (int i = 0; i < count; i++) {
			total += frames[i];
		}
		return total / count / 1000L;
	}

	public long percentileMicros(double p) {
		if (count == 0) {
			return 0L;
		}
		long[] sorted = Arrays.copyOf(frames, count);
		Arrays.sort(sorted);
		int index = (int) Math.round(p * (sorted.length - 1));
		return sorted[Math.max(0, Math.min(sorted.length - 1, index))] / 1000L;
	}
}
