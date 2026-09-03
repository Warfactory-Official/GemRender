package com.wf.gemrender.bedrock;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import com.wf.gemrender.bedrock.BedrockParts.RigidPart;
import com.wf.gemrender.gltf.GemRenderPartsModel;
import com.wf.gemrender.gltf.GltfAnimation;
import com.wf.gemrender.gltf.GltfPaletteLayout;
import com.wf.gemrender.gltf.GltfPose;
import com.wf.gemrender.gltf.MeshGeometry;
import com.wf.gemrender.gltf.NodeSpin;
import com.wf.gemrender.gltf.NodeTable;
import com.wf.gemrender.gltf.PartsPose;

class PartsPoseTest {

	@Test
	void partsFollowTheClipExactlyWhereTheSkinnedPaletteDoes() {
		for (Set<String> driven : List.of(Set.of("arm"), Set.of("hand"), Set.of("arm", "hand"))) {
			for (float time : new float[] { 0.0f, 0.37f, 1.5f, 3.25f }) {
				assertSamePicture(driven, driven, time);
			}
		}
	}

	@Test
	void overDeclaringAMovingBoneCostsPartsButChangesNothing() {
		for (float time : new float[] { 0.0f, 1.5f }) {
			assertSamePicture(Set.of("hand"), Set.of("root", "arm", "hand"), time);
		}
	}

	@Test
	void leavingADrivenBoneUncutIsTheOneWayToGetItWrong() {
		assertThat(maxDeviation(Set.of("arm", "hand"), Set.of(), 0.0f)).isLessThan(1.0e-4f);
		assertThat(maxDeviation(Set.of("arm", "hand"), Set.of(), 1.5f)).isGreaterThan(1.0e-3f);
	}

	@Test
	void evaluatingOnlyTheDrivenPartsGivesTheSameTransformsAsEvaluatingAll() {
		BedrockGeometry geometry = BedrockFixture.geometry(BedrockFixture.CHAIN);
		BedrockSkeleton skeleton = BedrockSkeleton.of(geometry);
		NodeTable table = skeleton.table();

		GltfAnimation clip = GltfAnimation.procedural("spin",
				NodeSpin.aboutY(table, table.slotOfName("arm"), 0.25f));

		GemRenderPartsModel model = partsModel(skeleton,
				BedrockParts.partition(geometry, skeleton, Set.of("arm", "hand")));

		boolean[] driven = model.drivenBy(clip);
		assertThat(GemRenderPartsModel.countTrue(driven)).as("arm and hand, but not root")
				.isEqualTo(2);

		PartsPose.Scratch scratch = new PartsPose.Scratch();
		Matrix4f[] full = model.newTransforms();
		Matrix4f[] partial = model.newTransforms();
		PartsPose.evaluate(model, null, 0.0f, partial, scratch);

		for (float time : new float[] { 0.4f, 1.9f, 3.3f }) {
			PartsPose.evaluate(model, clip, time, full, scratch);
			PartsPose.evaluate(model, clip, time, partial, driven, scratch);

			for (int i = 0; i < full.length; i++) {
				assertThat(partial[i]).as("part %s at t=%s", i, time)
						.isEqualTo(full[i]);
			}
		}
	}

	@Test
	void aDriverThatWillNotSayWhereItWritesForcesEveryPartOn() {
		BedrockGeometry geometry = BedrockFixture.geometry(BedrockFixture.CHAIN);
		BedrockSkeleton skeleton = BedrockSkeleton.of(geometry);

		GemRenderPartsModel model = partsModel(skeleton,
				BedrockParts.partition(geometry, skeleton, Set.of("arm", "hand")));

		GltfAnimation opaque = GltfAnimation.procedural("opaque", new com.wf.gemrender.gltf.PoseDriver() {
			@Override
			public void apply(float timeSeconds, float[] scratch) {
			}

			@Override
			public float cycleSeconds() {
				return 1.0f;
			}
		});

		assertThat(GemRenderPartsModel.countTrue(model.drivenBy(opaque))).isEqualTo(model.partCount());
	}

	private static void assertSamePicture(Set<String> driven, Set<String> moving, float time) {
		assertThat(maxDeviation(driven, moving, time)).as("driven=%s moving=%s at t=%s", driven, moving,
				time)
				.isLessThan(1.0e-4f);
	}

	private static float maxDeviation(Set<String> driven, Set<String> moving, float time) {
		BedrockGeometry geometry = BedrockFixture.geometry(BedrockFixture.CHAIN);
		BedrockSkeleton skeleton = BedrockSkeleton.of(geometry);
		NodeTable table = skeleton.table();

		List<com.wf.gemrender.gltf.PoseDriver> drivers = new ArrayList<>();
		if (driven.contains("arm")) {
			drivers.add(NodeSpin.aboutY(table, table.slotOfName("arm"), 0.25f));
		}
		if (driven.contains("hand")) {
			drivers.add(NodeSpin.about(table, table.slotOfName("hand"), 1.0f, 0.0f, 0.0f, 0.4f));
		}
		GltfAnimation clip = GltfAnimation.procedural("spin",
				drivers.toArray(new com.wf.gemrender.gltf.PoseDriver[0]));

		List<Vector3f> skinned = sorted(skinnedVertices(geometry, skeleton, clip, time));
		List<Vector3f> split = sorted(partVertices(geometry, skeleton, moving, clip, time));

		assertThat(split).hasSameSizeAs(skinned);

		float worst = 0.0f;
		for (int i = 0; i < skinned.size(); i++) {
			worst = Math.max(worst, split.get(i)
					.distance(skinned.get(i)));
		}
		return worst;
	}

	private static List<Vector3f> skinnedVertices(BedrockGeometry geometry, BedrockSkeleton skeleton,
			GltfAnimation clip, float time) {
		GltfPaletteLayout layout = GltfPaletteLayout.ofNodes(skeleton.table());
		Matrix4f[] palette = new Matrix4f[layout.size()];
		for (int i = 0; i < palette.length; i++) {
			palette[i] = new Matrix4f();
		}
		GltfPose.evaluate(layout, clip, time, palette);

		List<Vector3f> out = new ArrayList<>();
		for (Map.Entry<Integer, List<BedrockSkeleton.Part>> group : bySlot(skeleton).entrySet()) {
			BedrockCubes cubes = new BedrockCubes();
			for (BedrockSkeleton.Part part : group.getValue()) {
				cubes.add(part.cube(), part.pivot(), part.mirror(), part.inflate(),
						geometry.textureWidth(), geometry.textureHeight(), part.placement());
			}
			float[] positions = cubes.positions();
			for (int v = 0; v < cubes.vertexCount(); v++) {
				Vector3f point = new Vector3f(positions[v * 3], positions[v * 3 + 1], positions[v * 3 + 2]);
				palette[group.getKey()].transformPosition(point);
				out.add(point);
			}
		}
		return out;
	}

	private static List<Vector3f> partVertices(BedrockGeometry geometry, BedrockSkeleton skeleton,
			Set<String> moving, GltfAnimation clip, float time) {
		List<RigidPart> rigid = BedrockParts.partition(geometry, skeleton, moving);

		GemRenderPartsModel model = partsModel(skeleton, rigid);

		Matrix4f[] transforms = model.newTransforms();
		PartsPose.evaluate(model, clip, time, transforms, new PartsPose.Scratch());

		List<Vector3f> out = new ArrayList<>();
		for (int i = 0; i < rigid.size(); i++) {
			MeshGeometry mesh = rigid.get(i)
					.geometry();
			if (mesh == null) {
				continue;
			}
			for (int v = 0; v < mesh.vertexCount(); v++) {
				Vector3f point = new Vector3f(mesh.position(v, 0), mesh.position(v, 1), mesh.position(v, 2));
				transforms[i].transformPosition(point);
				out.add(point);
			}
		}
		return out;
	}

	private static GemRenderPartsModel partsModel(BedrockSkeleton skeleton, List<RigidPart> rigid) {
		List<GemRenderPartsModel.Part> parts = new ArrayList<>(rigid.size());
		int[] slotToPart = new int[skeleton.slotCount()];
		for (int i = 0; i < rigid.size(); i++) {
			RigidPart part = rigid.get(i);
			parts.add(new GemRenderPartsModel.Part(part.name(), part.rootSlot(), part.parent(),
					part.toParent(), null));
			for (int slot : part.slots()) {
				slotToPart[slot] = i;
			}
		}
		return new GemRenderPartsModel(parts, GltfPaletteLayout.ofNodes(skeleton.table()), slotToPart,
				Map.of(), new Vector4f(), 0, List.of());
	}

	private static List<Vector3f> sorted(List<Vector3f> points) {
		return points.stream()
				.sorted((a, b) -> {
					int x = Float.compare(round(a.x), round(b.x));
					if (x != 0) {
						return x;
					}
					int y = Float.compare(round(a.y), round(b.y));
					return y != 0 ? y : Float.compare(round(a.z), round(b.z));
				})
				.toList();
	}

	private static float round(float value) {
		return Math.round(value * 8192.0f) / 8192.0f;
	}

	private static Map<Integer, List<BedrockSkeleton.Part>> bySlot(BedrockSkeleton skeleton) {
		Map<Integer, List<BedrockSkeleton.Part>> grouped = new java.util.TreeMap<>();
		for (BedrockSkeleton.Part part : skeleton.parts()) {
			grouped.computeIfAbsent(part.slot(), key -> new ArrayList<>())
					.add(part);
		}
		return grouped;
	}
}
