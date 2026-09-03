package com.wf.gemrender.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FrameCostTest {
	private final FrameCost cost = FrameCost.getInstance();

	@BeforeEach
	void reset() {
		cost.endFrame();
		cost.endFrame();
		cost.resetRun();
	}

	@Test
	@DisplayName("reports nothing before the first frame boundary")
	void nothingBeforeEndFrame() {
		cost.addVisualNanos(500);
		cost.addPoseNanos(200);
		cost.addInstanceWrites(7);

		assertThat(cost.visualNanos()).isZero();
		assertThat(cost.poseNanos()).isZero();
		assertThat(cost.instanceWrites()).isZero();
	}

	@Test
	@DisplayName("sums a frame's contributions across visuals")
	void sumsAcrossVisuals() {
		cost.addVisualNanos(500);
		cost.addVisualNanos(300);
		cost.addPoseNanos(200);
		cost.addPoseNanos(100);
		cost.addInstanceWrites(7);
		cost.addInstanceWrites(3);

		cost.endFrame();

		assertThat(cost.visualNanos()).isEqualTo(800);
		assertThat(cost.poseNanos()).isEqualTo(300);
		assertThat(cost.instanceWrites()).isEqualTo(10);
	}

	@Test
	@DisplayName("overhead is visual time less pose time")
	void overheadIsTheRemainder() {
		cost.addVisualNanos(800);
		cost.addPoseNanos(300);
		cost.endFrame();

		assertThat(cost.overheadNanos()).isEqualTo(500);
	}

	@Test
	@DisplayName("overhead never reports negative")
	void overheadClampsAtZero() {
		cost.addPoseNanos(300);
		cost.endFrame();

		assertThat(cost.overheadNanos()).isZero();
	}

	@Test
	@DisplayName("a frame's cost does not leak into the next")
	void resetsBetweenFrames() {
		cost.addVisualNanos(800);
		cost.addPoseNanos(300);
		cost.addInstanceWrites(10);
		cost.endFrame();

		cost.addVisualNanos(50);
		cost.endFrame();

		assertThat(cost.visualNanos()).isEqualTo(50);
		assertThat(cost.poseNanos()).isZero();
		assertThat(cost.instanceWrites()).isZero();
	}

	@Test
	@DisplayName("an idle frame reports zero rather than the previous frame")
	void idleFrameReportsZero() {
		cost.addVisualNanos(800);
		cost.endFrame();
		cost.endFrame();

		assertThat(cost.visualNanos()).isZero();
	}

	@Test
	@DisplayName("zero writes are not counted")
	void zeroWritesAreSkipped() {
		cost.addInstanceWrites(0);
		cost.addInstanceWrites(4);
		cost.endFrame();

		assertThat(cost.instanceWrites()).isEqualTo(4);
	}

	@Test
	@DisplayName("means average only the frames that drew something")
	void meansSkipIdleFrames() {
		cost.addVisualNanos(1000);
		cost.addPoseNanos(600);
		cost.endFrame();

		cost.endFrame();
		cost.endFrame();
		cost.endFrame();

		cost.addVisualNanos(3000);
		cost.addPoseNanos(1400);
		cost.endFrame();

		assertThat(cost.sampledFrames()).isEqualTo(2);
		assertThat(cost.meanVisualNanos()).isEqualTo(2000);
		assertThat(cost.meanPoseNanos()).isEqualTo(1000);
		assertThat(cost.meanOverheadNanos()).isEqualTo(1000);
	}

	@Test
	@DisplayName("nanos per pose divides by poses, not by frames")
	void perPoseDividesByPoses() {
		cost.addVisualNanos(400);
		cost.addPoseNanos(100);
		cost.addPoseNanos(100);
		cost.addPoseNanos(100);
		cost.endFrame();

		cost.addVisualNanos(200);
		cost.addPoseNanos(100);
		cost.endFrame();

		assertThat(cost.meanPoseNanos()).isEqualTo(200);
		assertThat(cost.nanosPerPose()).isEqualTo(100);
	}

	@Test
	@DisplayName("a run reset drops the warm-up but not the frame in flight")
	void resetRunDropsHistoryOnly() {
		cost.addVisualNanos(9999);
		cost.addPoseNanos(9999);
		cost.endFrame();

		cost.resetRun();

		assertThat(cost.sampledFrames()).isZero();
		assertThat(cost.meanPoseNanos()).isZero();
		assertThat(cost.nanosPerPose()).isZero();

		cost.addVisualNanos(300);
		cost.addPoseNanos(100);
		cost.endFrame();

		assertThat(cost.sampledFrames()).isEqualTo(1);
		assertThat(cost.meanPoseNanos()).isEqualTo(100);
	}

	@Test
	@DisplayName("means report zero rather than dividing by no frames")
	void meansWithNoSamples() {
		assertThat(cost.sampledFrames()).isZero();
		assertThat(cost.meanVisualNanos()).isZero();
		assertThat(cost.meanPoseNanos()).isZero();
		assertThat(cost.meanOverheadNanos()).isZero();
		assertThat(cost.nanosPerPose()).isZero();
		assertThat(cost.meanInstanceWrites()).isZero();
	}

	@Test
	@DisplayName("loses nothing when visuals report from several threads")
	void countsEveryThreadsContribution() throws Exception {
		int threads = 8;
		int perThread = 1000;

		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch start = new CountDownLatch(1);
		try {
			for (int t = 0; t < threads; t++) {
				pool.submit(() -> {
					start.await();
					for (int i = 0; i < perThread; i++) {
						cost.addVisualNanos(3);
						cost.addPoseNanos(1);
						cost.addInstanceWrites(2);
					}
					return null;
				});
			}
			start.countDown();
			pool.shutdown();
			assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
		} finally {
			pool.shutdownNow();
		}

		cost.endFrame();

		assertThat(cost.visualNanos()).isEqualTo(3L * threads * perThread);
		assertThat(cost.poseNanos()).isEqualTo((long) threads * perThread);
		assertThat(cost.instanceWrites()).isEqualTo(2L * threads * perThread);
	}
}
