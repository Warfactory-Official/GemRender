package com.wf.gemrender.bedrock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import com.wf.gemrender.GemRender;
import com.wf.gemrender.bedrock.BedrockGeometry.Bone;
import com.wf.gemrender.bedrock.BedrockGeometry.Cube;
import com.wf.gemrender.gltf.NodeTable;

public final class BedrockSkeleton {
	public record Part(int slot, Cube cube, boolean mirror, float inflate, float[] pivot,
			@Nullable Matrix4f placement) {
	}

	private static final ThreadLocal<Quaternionf> SCRATCH = ThreadLocal.withInitial(Quaternionf::new);

	private final NodeTable table;

	private final float[] pivots;

	private final float[] restEuler;

	private final List<Part> parts;

	private BedrockSkeleton(NodeTable table, float[] pivots, float[] restEuler, List<Part> parts) {
		this.table = table;
		this.pivots = pivots;
		this.restEuler = restEuler;
		this.parts = parts;
	}

	public static BedrockSkeleton of(BedrockGeometry geometry) {
		List<Bone> bones = geometry.bones();
		int boneCount = bones.size();

		Map<String, Integer> byName = new HashMap<>();
		for (int i = 0; i < boneCount; i++) {
			String name = bones.get(i)
					.name();
			if (name != null && byName.putIfAbsent(name, i) != null) {
				GemRender.LOGGER.warn("Bedrock geometry has two bones named '{}'; a clip targeting it will "
						+ "drive the first, and the second will not animate.", name);
			}
		}

		String[] names = new String[boneCount];
		int[] parents = new int[boneCount];
		float[] pivots = new float[boneCount * 3];
		float[] restEuler = new float[boneCount * 3];

		for (int i = 0; i < boneCount; i++) {
			Bone bone = bones.get(i);
			names[i] = bone.name();
			System.arraycopy(flipX(bone.pivot()), 0, pivots, i * 3, 3);
			System.arraycopy(euler(bone.rotation()), 0, restEuler, i * 3, 3);

			Integer parent = bone.parent() == null ? null : byName.get(bone.parent());
			if (bone.parent() != null && parent == null) {
				GemRender.LOGGER.warn("Bedrock bone '{}' names parent '{}', which the file does not "
						+ "declare; treating it as a root.", bone.name(), bone.parent());
			}
			parents[i] = parent == null ? -1 : parent;
		}

		float[] trs = new float[boneCount * NodeTable.TRS_STRIDE];
		Quaternionf rotation = new Quaternionf();
		for (int slot = 0; slot < boneCount; slot++) {
			int parent = parents[slot];
			int base = slot * NodeTable.TRS_STRIDE;
			for (int axis = 0; axis < 3; axis++) {
				float local = pivots[slot * 3 + axis] - (parent < 0 ? 0.0f : pivots[parent * 3 + axis]);
				trs[base + NodeTable.TRANSLATION + axis] = local / 16.0f;
				trs[base + NodeTable.SCALE + axis] = 1.0f;
			}

			quaternion(restEuler[slot * 3], restEuler[slot * 3 + 1], restEuler[slot * 3 + 2], rotation);
			trs[base + NodeTable.ROTATION] = rotation.x;
			trs[base + NodeTable.ROTATION + 1] = rotation.y;
			trs[base + NodeTable.ROTATION + 2] = rotation.z;
			trs[base + NodeTable.ROTATION + 3] = rotation.w;
		}

		return new BedrockSkeleton(NodeTable.ofNodes(names, parents, trs), pivots, restEuler,
				parts(bones, pivots));
	}

	private static List<Part> parts(List<Bone> bones, float[] pivots) {
		List<Part> parts = new ArrayList<>();

		for (int i = 0; i < bones.size(); i++) {
			Bone bone = bones.get(i);
			float[] bonePivot = { pivots[i * 3], pivots[i * 3 + 1], pivots[i * 3 + 2] };

			for (Cube cube : bone.cubes()) {
				boolean mirror = cube.mirror() != null ? cube.mirror() : bone.mirror();
				float inflate = cube.inflate() != null ? cube.inflate()
						: bone.inflate() == null ? 0.0f : bone.inflate();

				if (cube.rotation() == null) {
					parts.add(new Part(i, cube, mirror, inflate, bonePivot, null));
					continue;
				}

				float[] cubePivot = flipX(cube.pivot() == null ? new float[3] : cube.pivot());
				float[] angles = euler(cube.rotation());

				Quaternionf turn = new Quaternionf();
				quaternion(angles[0], angles[1], angles[2], turn);
				Matrix4f placement = new Matrix4f().translate(
						(cubePivot[0] - bonePivot[0]) / 16.0f,
						(cubePivot[1] - bonePivot[1]) / 16.0f,
						(cubePivot[2] - bonePivot[2]) / 16.0f)
						.rotate(turn);

				parts.add(new Part(i, cube, mirror, inflate, cubePivot, placement));
			}
		}
		return parts;
	}

	public static void quaternion(float x, float y, float z, Quaternionf out) {
		out.rotationZYX(z, y, x);
	}

	static void quaternion(float x, float y, float z, float[] out, int offset) {
		Quaternionf rotation = SCRATCH.get();
		quaternion(x, y, z, rotation);

		out[offset] = rotation.x;
		out[offset + 1] = rotation.y;
		out[offset + 2] = rotation.z;
		out[offset + 3] = rotation.w;
	}

	static float[] euler(float[] degrees) {
		if (degrees == null) {
			return new float[3];
		}
		return new float[] {
				(float) Math.toRadians(-degrees[0]),
				(float) Math.toRadians(-degrees[1]),
				(float) Math.toRadians(degrees[2]),
		};
	}

	private static float[] flipX(float[] point) {
		return new float[] { -point[0], point[1], point[2] };
	}

	public NodeTable table() {
		return table;
	}

	public List<Part> parts() {
		return parts;
	}

	public float[] pivot(int slot) {
		return new float[] { pivots[slot * 3], pivots[slot * 3 + 1], pivots[slot * 3 + 2] };
	}

	public float restEuler(int slot, int axis) {
		return restEuler[slot * 3 + axis];
	}

	public int slotCount() {
		return table.nodeCount();
	}
}
