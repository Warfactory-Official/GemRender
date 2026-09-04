package com.wf.gemrender.render;

import java.util.concurrent.atomic.LongAdder;

public final class FrameCost {
	private static final FrameCost INSTANCE = new FrameCost();

	private final LongAdder visualNanos = new LongAdder();
	private final LongAdder poseNanos = new LongAdder();
	private final LongAdder poseCount = new LongAdder();
	private final LongAdder instanceWrites = new LongAdder();

	private final LongAdder uploadNanos = new LongAdder();

	private volatile long visualNanosLastFrame;
	private volatile long poseNanosLastFrame;
	private volatile long poseCountLastFrame;
	private volatile long instanceWritesLastFrame;
	private volatile long uploadNanosLastFrame;

	private volatile long sampledFrames;
	private volatile long runVisualNanos;
	private volatile long runPoseNanos;
	private volatile long runPoseCount;
	private volatile long runInstanceWrites;
	private volatile long runUploadNanos;

	private FrameCost() {
	}

	public static FrameCost getInstance() {
		return INSTANCE;
	}

	public void addVisualNanos(long nanos) {
		visualNanos.add(nanos);
	}

	public void addPoseNanos(long nanos) {
		poseNanos.add(nanos);
		poseCount.increment();
	}

	public void addUploadNanos(long nanos) {
		uploadNanos.add(nanos);
	}

	public void addInstanceWrites(int writes) {
		if (writes > 0) {
			instanceWrites.add(writes);
		}
	}

	public void endFrame() {
		visualNanosLastFrame = visualNanos.sumThenReset();
		poseNanosLastFrame = poseNanos.sumThenReset();
		poseCountLastFrame = poseCount.sumThenReset();
		instanceWritesLastFrame = instanceWrites.sumThenReset();
		uploadNanosLastFrame = uploadNanos.sumThenReset();

		if (visualNanosLastFrame > 0 || uploadNanosLastFrame > 0) {
			sampledFrames++;
			runVisualNanos += visualNanosLastFrame;
			runPoseNanos += poseNanosLastFrame;
			runPoseCount += poseCountLastFrame;
			runInstanceWrites += instanceWritesLastFrame;
			runUploadNanos += uploadNanosLastFrame;
		}
	}

	public void resetRun() {
		sampledFrames = 0;
		runVisualNanos = 0;
		runPoseNanos = 0;
		runPoseCount = 0;
		runInstanceWrites = 0;
		runUploadNanos = 0;
	}

	public long sampledFrames() {
		return sampledFrames;
	}

	public long meanVisualNanos() {
		return mean(runVisualNanos);
	}

	public long meanPoseNanos() {
		return mean(runPoseNanos);
	}

	public long meanOverheadNanos() {
		return mean(Math.max(0L, runVisualNanos - runPoseNanos));
	}

	public long nanosPerPose() {
		return runPoseCount == 0 ? 0 : runPoseNanos / runPoseCount;
	}

	public long meanPoseCount() {
		return mean(runPoseCount);
	}

	public long meanUploadNanos() {
		return mean(runUploadNanos);
	}

	public long meanInstanceWrites() {
		return mean(runInstanceWrites);
	}

	private long mean(long total) {
		return sampledFrames == 0 ? 0 : total / sampledFrames;
	}

	public long visualNanos() {
		return visualNanosLastFrame;
	}

	public long poseNanos() {
		return poseNanosLastFrame;
	}

	public long overheadNanos() {
		return Math.max(0L, visualNanosLastFrame - poseNanosLastFrame);
	}

	public long instanceWrites() {
		return instanceWritesLastFrame;
	}

	public long uploadNanos() {
		return uploadNanosLastFrame;
	}
}
