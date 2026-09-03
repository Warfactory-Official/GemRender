package com.wf.gemrender.bedrock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Map;

import org.joml.Quaternionf;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.wf.gemrender.gltf.GltfAnimation;
import com.wf.gemrender.gltf.NodeTable;

class BedrockAnimationsTest {

	private final BedrockSkeleton unit = BedrockFixture.skeleton(BedrockFixture.UNIT);
	private final BedrockSkeleton chain = BedrockFixture.skeleton(BedrockFixture.CHAIN);

	private static Map<String, GltfAnimation> parse(String json, BedrockSkeleton skeleton) {
		return BedrockAnimations.parse(BedrockFixture.json(json), skeleton, "test");
	}

	private static float[] pose(BedrockSkeleton skeleton, GltfAnimation clip, float time) {
		float[] scratch = skeleton.table()
				.newScratch();
		clip.apply(time, scratch);
		return scratch;
	}

	@Test
	@DisplayName("position is negated on X, divided by sixteen, and added to the rest translation")
	void positionConvention() {
		Map<String, GltfAnimation> clips = parse("""
				{"animations": {"slide": {"animation_length": 1,
				  "bones": {"cube": {"position": {"0.0": [0,0,0], "1.0": [16, 32, 48]}}}}}}
				""", unit);

		float[] scratch = pose(unit, clips.get("slide"), 1.0f);
		int base = NodeTable.TRANSLATION;

		assertThat(scratch[base]).isCloseTo(-1.0f, within(1e-6f));
		assertThat(scratch[base + 1]).isCloseTo(2.0f, within(1e-6f));
		assertThat(scratch[base + 2]).isCloseTo(3.0f, within(1e-6f));

		assertThat(pose(unit, clips.get("slide"), 0.5f)[base]).isCloseTo(-0.5f, within(1e-6f));
	}

	@Test
	@DisplayName("rotation is added to the bone's rest angles, in Euler space")
	void rotationIsAdditiveInEulerSpace() {
		Map<String, GltfAnimation> clips = parse("""
				{"animations": {"bend": {"animation_length": 1,
				  "bones": {"root": {"rotation": {"0.0": [0,0,0], "1.0": [40, 0, 40]}}}}}}
				""", chain);

		Quaternionf atRest = rotationOf(pose(chain, clips.get("bend"), 0.0f), 0);
		Quaternionf expectedRest = euler(-10.0, -20.0, 30.0);
		assertQuaternion(atRest, expectedRest);

		Quaternionf bent = rotationOf(pose(chain, clips.get("bend"), 1.0f), 0);
		Quaternionf expectedBent = euler(-50.0, -20.0, 70.0);
		assertQuaternion(bent, expectedBent);

		Quaternionf clipOnly = euler(-40.0, 0.0, 40.0);
		assertThat(degreesBetween(new Quaternionf(expectedRest).mul(clipOnly), expectedBent))
				.as("rest * clip is a visibly different orientation")
				.isGreaterThan(5.0);
		assertThat(degreesBetween(new Quaternionf(clipOnly).mul(expectedRest), expectedBent))
				.as("clip * rest is a visibly different orientation")
				.isGreaterThan(5.0);
	}

	private static Quaternionf euler(double x, double y, double z) {
		return new Quaternionf().rotateZ((float) Math.toRadians(z))
				.rotateY((float) Math.toRadians(y))
				.rotateX((float) Math.toRadians(x));
	}

	private static double degreesBetween(Quaternionf a, Quaternionf b) {
		double dot = Math.abs(a.difference(b, new Quaternionf()).w);
		return Math.toDegrees(2.0 * Math.acos(Math.min(dot, 1.0)));
	}

	@Test
	@DisplayName("a clip of all-zero rotations leaves the model exactly at rest")
	void zeroRotationIsTheRestPose() {
		Map<String, GltfAnimation> clips = parse("""
				{"animations": {"idle": {"animation_length": 1, "bones": {
				  "root": {"rotation": {"0.0": [0,0,0], "1.0": [0,0,0]}},
				  "arm": {"rotation": {"0.0": [0,0,0], "1.0": [0,0,0]}}}}}}
				""", chain);

		float[] rest = chain.table()
				.newScratch();
		float[] posed = pose(chain, clips.get("idle"), 0.4f);

		for (int i = 0; i < rest.length; i++) {
			assertThat(posed[i]).isCloseTo(rest[i], within(1e-6f));
		}
	}

	@Test
	@DisplayName("scale multiplies, and defaults to leaving the bone alone")
	void scaleConvention() {
		Map<String, GltfAnimation> clips = parse("""
				{"animations": {"grow": {"animation_length": 1,
				  "bones": {"cube": {"scale": {"0.0": 1, "1.0": [2, 3, 4]}}}}}}
				""", unit);

		float[] scratch = pose(unit, clips.get("grow"), 1.0f);
		assertThat(scratch[NodeTable.SCALE]).isCloseTo(2.0f, within(1e-6f));
		assertThat(scratch[NodeTable.SCALE + 1]).isCloseTo(3.0f, within(1e-6f));
		assertThat(scratch[NodeTable.SCALE + 2]).isCloseTo(4.0f, within(1e-6f));

		assertThat(pose(unit, clips.get("grow"), 0.0f)[NodeTable.SCALE]).isCloseTo(1.0f, within(1e-6f));
	}

	@Test
	@DisplayName("every shape a channel may take is read")
	void channelShapes() {
		BedrockSkeleton skeleton = unit;

		assertThat(pose(skeleton, parse("""
				{"animations": {"a": {"bones": {"cube": {"position": [16, 0, 0]}}}}}
				""", skeleton)
				.get("a"), 5.0f)[NodeTable.TRANSLATION]).isCloseTo(-1.0f, within(1e-6f));

		assertThat(pose(skeleton, parse("""
				{"animations": {"a": {"bones": {"cube":
				  {"position": {"vector": [16, 0, 0], "easing": "easeinquad"}}}}}}
				""", skeleton)
				.get("a"), 5.0f)[NodeTable.TRANSLATION]).isCloseTo(-1.0f, within(1e-6f));

		float[] molang = pose(skeleton, parse("""
				{"animations": {"a": {"bones": {"cube": {"position": "q.anim_time * 16"}}}}}
				""", skeleton)
				.get("a"), 0.5f);
		assertThat(molang[NodeTable.TRANSLATION]).isCloseTo(-0.5f, within(1e-6f));
		assertThat(molang[NodeTable.TRANSLATION + 1]).isCloseTo(0.5f, within(1e-6f));
	}

	@Test
	@DisplayName("a keyframe's pre and post sides are read, and lerp_mode reaches the segment")
	void keyframeShapes() {
		Map<String, GltfAnimation> clips = parse("""
				{"animations": {"cut": {"animation_length": 2, "bones": {"cube": {"position": {
				  "0.0": [0, 0, 0],
				  "1.0": {"pre": [0, 0, 0], "post": [160, 0, 0], "lerp_mode": "linear"},
				  "2.0": {"vector": [160, 0, 0]}}}}}}}
				""", unit);

		GltfAnimation clip = clips.get("cut");
		assertThat(pose(unit, clip, 0.99f)[NodeTable.TRANSLATION]).isCloseTo(0.0f, within(0.01f));
		assertThat(pose(unit, clip, 1.0f)[NodeTable.TRANSLATION]).as("the cut at the key")
				.isCloseTo(-10.0f, within(1e-5f));
		assertThat(clip.duration()).isEqualTo(2.0f);
	}

	@Test
	@DisplayName("a nested vector inside pre or post is unwrapped")
	void nestedVectorInsidePreAndPost() {
		Map<String, GltfAnimation> clips = parse("""
				{"animations": {"a": {"bones": {"cube": {"position": {
				  "0.0": {"post": {"vector": [16, 0, 0]}}}}}}}}
				""", unit);

		assertThat(pose(unit, clips.get("a"), 0.0f)[NodeTable.TRANSLATION]).isCloseTo(-1.0f, within(1e-6f));
	}

	@Test
	@DisplayName("a channel using Molang outside the subset is dropped, and the bone keeps its rest pose")
	void unsupportedMolangDropsOnlyThatChannel() {
		Map<String, GltfAnimation> clips = parse("""
				{"animations": {"a": {"animation_length": 1, "bones": {"cube": {
				  "position": {"0.0": ["query.life_time", 0, 0]},
				  "scale": {"0.0": 2}}}}}}
				""", unit);

		float[] scratch = pose(unit, clips.get("a"), 0.5f);
		assertThat(scratch[NodeTable.TRANSLATION]).as("the refused channel leaves the rest translation")
				.isEqualTo(0.0f);
		assertThat(scratch[NodeTable.SCALE]).as("the channel beside it is untouched")
				.isCloseTo(2.0f, within(1e-6f));
	}

	@Test
	@DisplayName("a keyframe with no value in it loses its channel, not the clip and not the file")
	void malformedKeyframesAreContained() {
		Map<String, GltfAnimation> clips = parse("""
				{"animations": {
				  "broken": {"animation_length": 1, "bones": {"cube": {
				    "position": {"0.0": {"lerp_mode": "linear"}},
				    "scale": {"0.0": 2}}}},
				  "fine": {"animation_length": 1, "bones": {"cube": {"scale": {"0.0": 3}}}}}}
				""", unit);

		assertThat(clips).containsOnlyKeys("broken", "fine");
		assertThat(clips.get("broken")
				.drivers()).hasSize(1);
		assertThat(pose(unit, clips.get("broken"), 0.0f)[NodeTable.TRANSLATION]).isEqualTo(0.0f);
		assertThat(pose(unit, clips.get("broken"), 0.0f)[NodeTable.SCALE]).isCloseTo(2.0f, within(1e-6f));
		assertThat(pose(unit, clips.get("fine"), 0.0f)[NodeTable.SCALE]).isCloseTo(3.0f, within(1e-6f));
	}

	@Test
	@DisplayName("a clip that will not parse at all loses only itself")
	void unparseableClipsAreContained() {
		Map<String, GltfAnimation> clips = parse("""
				{"animations": {
				  "broken": {"animation_length": 1, "bones": true},
				  "fine": {"animation_length": 1, "bones": {"cube": {"scale": {"0.0": 3}}}}}}
				""", unit);

		assertThat(clips).containsOnlyKeys("fine");
		assertThat(pose(unit, clips.get("fine"), 0.0f)[NodeTable.SCALE]).isCloseTo(3.0f, within(1e-6f));
	}

	@Test
	@DisplayName("a clip naming a bone the model does not have loses that bone, not the clip")
	void unknownBonesAreSkipped() {
		Map<String, GltfAnimation> clips = parse("""
				{"animations": {"a": {"animation_length": 1, "bones": {
				  "cube": {"scale": 2},
				  "ghost": {"scale": 3}}}}}
				""", unit);

		assertThat(clips.get("a")
				.drivers()).hasSize(1);
	}

	@Test
	@DisplayName("a clip that drives nothing at all is dropped rather than kept empty")
	void emptyClipsAreDropped() {
		assertThat(parse("""
				{"animations": {"a": {"animation_length": 1, "bones": {"ghost": {"scale": 3}}}}}
				""", unit)).isEmpty();
	}

	@Test
	@DisplayName("the declared length wins, and the last keyframe is the fallback")
	void durationComesFromTheFile() {
		assertThat(parse("""
				{"animations": {"a": {"animation_length": 5,
				  "bones": {"cube": {"scale": {"0.0": 1, "1.0": 2}}}}}}
				""", unit)
				.get("a")
				.duration()).isEqualTo(5.0f);

		assertThat(parse("""
				{"animations": {"a": {"bones": {"cube": {"scale": {"0.0": 1, "1.5": 2}}}}}}
				""", unit)
				.get("a")
				.duration()).isEqualTo(1.5f);
	}

	@Test
	@DisplayName("two identical clips compare equal, so the pose cache can share their evaluations")
	void clipsHaveValueEquality() {
		String json = """
				{"animations": {"a": {"animation_length": 1, "bones": {"cube": {
				  "position": {"0.0": [0,0,0], "1.0": [16,0,0]},
				  "rotation": {"0.0": [0,0,0], "1.0": [90,0,0]}}}}}}
				""";

		GltfAnimation first = parse(json, unit)
				.get("a");
		GltfAnimation second = parse(json, unit)
				.get("a");

		assertThat(first).isEqualTo(second)
				.hasSameHashCodeAs(second);
		assertThat(first.drivers()
				.get(0)).isEqualTo(second.drivers()
						.get(0));
	}

	@Test
	@DisplayName("a driver is a pure function of time")
	void driversArePure() {
		GltfAnimation clip = parse("""
				{"animations": {"a": {"animation_length": 2, "bones": {"cube": {
				  "position": {"0.0": [0,0,0], "2.0": [32,0,0]}}}}}}
				""", unit)
				.get("a");

		float first = pose(unit, clip, 1.0f)[NodeTable.TRANSLATION];
		pose(unit, clip, 0.0f);
		pose(unit, clip, 2.0f);
		assertThat(pose(unit, clip, 1.0f)[NodeTable.TRANSLATION]).isEqualTo(first);
	}

	private static Quaternionf rotationOf(float[] scratch, int slot) {
		int base = slot * NodeTable.TRS_STRIDE + NodeTable.ROTATION;
		return new Quaternionf(scratch[base], scratch[base + 1], scratch[base + 2], scratch[base + 3]);
	}

	private static void assertQuaternion(Quaternionf actual, Quaternionf expected) {
		assertThat(actual.x).isCloseTo(expected.x, within(1e-5f));
		assertThat(actual.y).isCloseTo(expected.y, within(1e-5f));
		assertThat(actual.z).isCloseTo(expected.z, within(1e-5f));
		assertThat(actual.w).isCloseTo(expected.w, within(1e-5f));
	}
}
