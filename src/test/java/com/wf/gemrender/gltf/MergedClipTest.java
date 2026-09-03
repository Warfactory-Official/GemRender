package com.wf.gemrender.gltf;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class MergedClipTest {
	private record Recorder(List<Float> times, float cycleSeconds) implements PoseDriver {
		@Override
		public void apply(float timeSeconds, float[] scratch) {
			times.add(timeSeconds);
		}

		@Override
		public int offset() {
			return 0;
		}
	}

	private static Recorder recorder(float cycleSeconds) {
		return new Recorder(new ArrayList<>(), cycleSeconds);
	}

	@Test
	void mergingWithALongerLayerWidensTheClipToTheLongerPeriod() {
		GltfAnimation clip = GltfAnimation.procedural("tread", recorder(1.0f));

		assertThat(clip.duration()).isEqualTo(1.0f);
		assertThat(clip.with(recorder(20.0f))
				.duration()).isEqualTo(20.0f);
	}

	@Test
	void theShorterLayerKeepsLoopingInsideTheLongerOne() {
		Recorder tread = recorder(1.0f);
		GltfAnimation merged = GltfAnimation.procedural("tread", tread)
				.with(recorder(20.0f));

		float[] scratch = new float[4];
		merged.apply(5.5f, scratch);
		merged.apply(19.25f, scratch);

		assertThat(tread.times()).containsExactly(0.5f, 0.25f);
	}

	@Test
	void theLongerLayerGetsTheWholeClockUntouched() {
		Recorder spin = recorder(20.0f);
		GltfAnimation merged = GltfAnimation.procedural("tread", recorder(1.0f))
				.with(spin);

		merged.apply(5.5f, new float[4]);

		assertThat(spin.times()).containsExactly(5.5f);
	}

	@Test
	void mergingWithAShorterLayerLeavesTheClipAloneEntirely() {
		Recorder tread = recorder(20.0f);
		GltfAnimation merged = GltfAnimation.procedural("tread", tread)
				.with(recorder(1.0f));

		assertThat(merged.duration()).isEqualTo(20.0f);

		merged.apply(5.5f, new float[4]);
		assertThat(tread.times()).as("no loop wrapper is needed, so none is added")
				.containsExactly(5.5f);
	}

	@Test
	void aLoopedDriverStillSaysWhereItWrites() {
		PoseDriver inner = new PoseChannel(null, 37, 4);
		assertThat(Looped.of(inner, 2.0f)
				.offset()).isEqualTo(37);
	}

	@Test
	void loopingAtAZeroPeriodIsLeftAlone() {
		PoseDriver inner = recorder(0.0f);
		assertThat(Looped.of(inner, 0.0f)).isSameAs(inner);
	}
}
