package com.wf.gemrender.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.joml.Matrix4f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.wf.gemrender.gltf.GltfAnimation;
import com.wf.gemrender.gltf.RigFixture;
import com.wf.gemrender.gltf.skin.SkinnedBounds;

class BoneSocketTest {
	private static final float QUANTUM = 0.1f;

	private static final float TOLERANCE = 1.0e-6f;

	private static PoseCache cache() {
		return new PoseCache(QUANTUM);
	}

	private static SkinnedBounds bounds() {
		return RigFixture.bounds();
	}

	private static void assertMatches(Matrix4f actual, Matrix4f expected, String what) {
		for (int column = 0; column < 4; column++) {
			for (int row = 0; row < 4; row++) {
				assertThat(actual.get(column, row))
						.as(what + ", m" + column + row)
						.isEqualTo(expected.get(column, row), org.assertj.core.api.Assertions.within(TOLERANCE));
			}
		}
	}

	@Test
	@DisplayName("a socket is the bone's global transform, matching an independent evaluation")
	void socketMatchesAnIndependentEvaluation() {
		PoseCache cache = cache();
		GltfAnimation clip = RigFixture.animation();

		PoseCache.Pose pose = cache.pose(RigFixture.layout(), bounds(), clip, RigFixture.MID_CLIP);

		Matrix4f[] oracle = RigFixture.pose(clip, RigFixture.MID_CLIP);
		for (int slot = 0; slot < RigFixture.NODE_COUNT; slot++) {
			assertMatches(pose.boneMatrix(slot, new Matrix4f()), oracle[slot], "node " + slot);
		}
	}

	@Test
	@DisplayName("a socket moves with the clip")
	void socketTracksTheClip() {
		PoseCache cache = cache();
		GltfAnimation clip = RigFixture.animation();

		Matrix4f[] atRest = RigFixture.pose(clip, 0.0f);
		Matrix4f[] atMid = RigFixture.pose(clip, RigFixture.MID_CLIP);

		int moving = -1;
		for (int slot = 0; slot < RigFixture.NODE_COUNT; slot++) {
			if (!atRest[slot].equals(atMid[slot])) {
				moving = slot;
				break;
			}
		}
		assertThat(moving)
				.as("the fixture's clip has to move some bone for this to test anything")
				.isNotEqualTo(-1);

		Matrix4f rest = cache.pose(RigFixture.layout(), bounds(), clip, 0.0f)
				.boneMatrix(moving, new Matrix4f());
		Matrix4f curled = cache.pose(RigFixture.layout(), bounds(), clip, RigFixture.MID_CLIP)
				.boneMatrix(moving, new Matrix4f());

		assertMatches(rest, atRest[moving], "at rest");
		assertMatches(curled, atMid[moving], "mid-curl");
		assertThat(curled)
				.as("the curl has to reach the socket, or it is reading a rest pose")
				.isNotEqualTo(rest);
	}

	@Test
	@DisplayName("a bone can be asked for by the name the asset gave it")
	void socketByName() {
		PoseCache cache = cache();
		GltfAnimation clip = RigFixture.animation();

		PoseCache.Pose pose = cache.pose(RigFixture.layout(), bounds(), clip, RigFixture.MID_CLIP);

		String name = RigFixture.layout()
				.nodeTable()
				.nodeName(RigFixture.NODE_BONE0);

		assertMatches(pose.boneMatrix(name, new Matrix4f()),
				pose.boneMatrix(RigFixture.NODE_BONE0, new Matrix4f()), "by name");

		assertThatThrownBy(() -> pose.boneMatrix("no-such-bone", new Matrix4f()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("no-such-bone");
	}

	@Test
	@DisplayName("a skin's palette slot is refused rather than answered wrongly")
	void skinSlotsAreRefused() {
		PoseCache cache = cache();
		PoseCache.Pose pose = cache.pose(RigFixture.layout(), bounds(), RigFixture.animation(),
				RigFixture.MID_CLIP);

		assertThatThrownBy(() -> pose.boneMatrix(RigFixture.SKIN_BASE, new Matrix4f()))
				.as("global x inverseBind is not where the joint is, and looks plausible enough to ship")
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("skin");

		assertThatThrownBy(() -> pose.boneMatrix(-1, new Matrix4f()))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("copies sharing one evaluation agree about where their bones are")
	void sharedPosesAgree() {
		PoseCache cache = cache();
		GltfAnimation clip = RigFixture.animation();

		PoseCache.Pose first = cache.pose(RigFixture.layout(), bounds(), clip, RigFixture.MID_CLIP);
		PoseCache.Pose second = cache.pose(RigFixture.layout(), bounds(), clip, RigFixture.MID_CLIP);

		assertThat(second.boneBase()).isEqualTo(first.boneBase());
		assertMatches(second.boneMatrix(RigFixture.NODE_BONE0, new Matrix4f()),
				first.boneMatrix(RigFixture.NODE_BONE0, new Matrix4f()), "shared pose");
	}

	@Test
	@DisplayName("a later evaluation in the same frame does not overwrite an earlier pose's bones")
	void posesInOneFrameDoNotShareAPalette() {
		PoseCache cache = cache();
		GltfAnimation clip = RigFixture.animation();

		PoseCache.Pose early = cache.pose(RigFixture.layout(), bounds(), clip, 0.5f);
		PoseCache.Pose late = cache.pose(RigFixture.layout(), bounds(), clip, 1.0f);

		assertMatches(early.boneMatrix(RigFixture.NODE_BONE0, new Matrix4f()),
				RigFixture.pose(clip, 0.5f)[RigFixture.NODE_BONE0], "the earlier pose");
		assertMatches(late.boneMatrix(RigFixture.NODE_BONE0, new Matrix4f()),
				RigFixture.pose(clip, 1.0f)[RigFixture.NODE_BONE0], "the later pose");
	}

	@Test
	@DisplayName("palettes are recycled, so a steady crowd allocates none after the first frame")
	void palettesAreRecycled() {
		PoseCache cache = cache();
		GltfAnimation clip = RigFixture.animation();
		int size = RigFixture.layout()
				.size();

		cache.pose(RigFixture.layout(), bounds(), clip, 0.5f);
		cache.pose(RigFixture.layout(), bounds(), clip, 1.0f);
		assertThat(cache.pooledPalettes(size))
				.as("palettes are in use while the frame is open")
				.isZero();

		cache.endFrame();
		assertThat(cache.pooledPalettes(size))
				.as("and go back to the pool when it closes")
				.isEqualTo(2);

		cache.pose(RigFixture.layout(), bounds(), clip, 0.5f);
		cache.pose(RigFixture.layout(), bounds(), clip, 1.0f);
		assertThat(cache.pooledPalettes(size))
				.as("the next frame's poses come out of the pool rather than the heap")
				.isZero();
	}
}
