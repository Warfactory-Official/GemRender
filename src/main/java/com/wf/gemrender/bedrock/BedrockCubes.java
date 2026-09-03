package com.wf.gemrender.bedrock;

import java.util.Arrays;
import java.util.Map;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

import com.wf.gemrender.bedrock.BedrockGeometry.Cube;
import com.wf.gemrender.bedrock.BedrockGeometry.Face;
import com.wf.gemrender.bedrock.BedrockGeometry.FaceUv;

public final class BedrockCubes {
	static final int[][] QUAD = {
			{ 0, 1, 5, 4 },
			{ 6, 7, 3, 2 },
			{ 2, 3, 1, 0 },
			{ 7, 6, 4, 5 },
			{ 6, 2, 0, 4 },
			{ 3, 7, 5, 1 },
	};

	static final float[][] NORMAL = {
			{ 0.0f, -1.0f, 0.0f }, { 0.0f, 1.0f, 0.0f },
			{ 0.0f, 0.0f, -1.0f }, { 0.0f, 0.0f, 1.0f },
			{ -1.0f, 0.0f, 0.0f }, { 1.0f, 0.0f, 0.0f },
	};

	private float[] positions = new float[96];
	private float[] normals = new float[96];
	private float[] texCoords = new float[64];
	private int[] indices = new int[48];

	private int vertexCount;
	private int indexCount;
	private int droppedFaces;

	public void add(Cube cube, float[] pivot, boolean mirror, float inflate, int textureWidth,
			int textureHeight) {
		add(cube, pivot, mirror, inflate, textureWidth, textureHeight, null);
	}

	public void add(Cube cube, float[] pivot, boolean mirror, float inflate, int textureWidth,
			int textureHeight, @Nullable Matrix4fc placement) {
		float sizeX = cube.size()[0];
		float sizeY = cube.size()[1];
		float sizeZ = cube.size()[2];

		float minX = (-(cube.origin()[0] + sizeX) - pivot[0] - inflate) / 16.0f;
		float minY = (cube.origin()[1] - pivot[1] - inflate) / 16.0f;
		float minZ = (cube.origin()[2] - pivot[2] - inflate) / 16.0f;

		float width = Math.max(0.0f, (sizeX + inflate * 2.0f) / 16.0f);
		float height = Math.max(0.0f, (sizeY + inflate * 2.0f) / 16.0f);
		float depth = Math.max(0.0f, (sizeZ + inflate * 2.0f) / 16.0f);

		float[] corners = new float[24];
		Vector3f scratch = new Vector3f();
		for (int c = 0; c < 8; c++) {
			scratch.set((c & 1) == 0 ? minX : minX + width,
					(c & 2) == 0 ? minY : minY + height,
					(c & 4) == 0 ? minZ : minZ + depth);
			if (placement != null) {
				placement.transformPosition(scratch);
			}
			corners[c * 3] = scratch.x;
			corners[c * 3 + 1] = scratch.y;
			corners[c * 3 + 2] = scratch.z;
		}

		float[][] faceNormals = NORMAL;
		if (placement != null) {
			faceNormals = new float[6][3];
			for (int face = 0; face < 6; face++) {
				scratch.set(NORMAL[face][0], NORMAL[face][1], NORMAL[face][2]);
				placement.transformDirection(scratch);
				faceNormals[face][0] = scratch.x;
				faceNormals[face][1] = scratch.y;
				faceNormals[face][2] = scratch.z;
			}
		}

		float[] uv = new float[8];
		for (Face face : Face.values()) {
			if (spanA(face, width, height, depth) == 0.0f || spanB(face, width, height, depth) == 0.0f) {
				droppedFaces++;
				continue;
			}
			if (!faceUvs(cube, face, mirror, textureWidth, textureHeight, uv)) {
				droppedFaces++;
				continue;
			}
			emit(face, corners, faceNormals[face.ordinal()], uv);
		}
	}

	private static float spanA(Face face, float width, float height, float depth) {
		return face == Face.WEST || face == Face.EAST ? depth : width;
	}

	private static float spanB(Face face, float width, float height, float depth) {
		return face == Face.DOWN || face == Face.UP ? depth : height;
	}

	private static boolean faceUvs(Cube cube, Face face, boolean mirror, int textureWidth,
			int textureHeight, float[] out) {
		Face source = mirror && (face == Face.WEST || face == Face.EAST) ? face.opposite() : face;

		float u0;
		float uSize;
		float v0;
		float vSize;
		int rotation = 0;

		if (cube.boxUv() != null) {
			float dx = (float) Math.floor(cube.size()[0]);
			float dy = (float) Math.floor(cube.size()[1]);
			float dz = (float) Math.floor(cube.size()[2]);
			float u = cube.boxUv()[0];
			float v = cube.boxUv()[1];

			u0 = switch (source) {
				case EAST -> u;
				case NORTH, UP -> u + dz;
				case DOWN, WEST -> u + dz + dx;
				case SOUTH -> u + dz + dx + dz;
			};
			uSize = source == Face.WEST || source == Face.EAST ? dz : dx;
			v0 = source == Face.UP ? v : v + dz;

			vSize = switch (source) {
				case UP -> dz;
				case DOWN -> -dz;
				default -> dy;
			};
		} else {
			Map<Face, FaceUv> faces = cube.faceUv();
			FaceUv entry = faces == null ? null : faces.get(source);
			if (entry == null) {
				return false;
			}
			u0 = entry.uv()[0];
			uSize = entry.uvSize()[0];
			v0 = entry.uv()[1];
			vSize = entry.uvSize()[1];
			rotation = entry.rotation();
		}

		float uMin = u0 / textureWidth;
		float uMax = (u0 + uSize) / textureWidth;
		float vMin = v0 / textureHeight;
		float vMax = (v0 + vSize) / textureHeight;

		float uFirst = mirror ? uMin : uMax;
		float uSecond = mirror ? uMax : uMin;

		out[0] = uFirst;
		out[1] = vMin;
		out[2] = uSecond;
		out[3] = vMin;
		out[4] = uSecond;
		out[5] = vMax;
		out[6] = uFirst;
		out[7] = vMax;

		rotate(out, rotation);
		return true;
	}

	private static void rotate(float[] uv, int degrees) {
		if (degrees % 90 != 0) {
			return;
		}
		int steps = Math.floorMod(degrees / 90, 4);
		if (steps == 0) {
			return;
		}

		float[] source = uv.clone();
		for (int corner = 0; corner < 4; corner++) {
			int from = (corner + steps) % 4;
			uv[corner * 2] = source[from * 2];
			uv[corner * 2 + 1] = source[from * 2 + 1];
		}
	}

	private void emit(Face face, float[] corners, float[] normal, float[] uv) {
		int base = vertexCount;
		positions = grow(positions, (base + 4) * 3);
		normals = grow(normals, (base + 4) * 3);
		texCoords = grow(texCoords, (base + 4) * 2);
		indices = grow(indices, indexCount + 6);

		int[] quad = QUAD[face.ordinal()];
		for (int i = 0; i < 4; i++) {
			int corner = quad[i];
			System.arraycopy(corners, corner * 3, positions, (base + i) * 3, 3);
			System.arraycopy(normal, 0, normals, (base + i) * 3, 3);
			texCoords[(base + i) * 2] = uv[i * 2];
			texCoords[(base + i) * 2 + 1] = uv[i * 2 + 1];
		}

		indices[indexCount] = base;
		indices[indexCount + 1] = base + 1;
		indices[indexCount + 2] = base + 2;
		indices[indexCount + 3] = base;
		indices[indexCount + 4] = base + 2;
		indices[indexCount + 5] = base + 3;

		vertexCount += 4;
		indexCount += 6;
	}

	public int vertexCount() {
		return vertexCount;
	}

	public int droppedFaces() {
		return droppedFaces;
	}

	public float[] positions() {
		return Arrays.copyOf(positions, vertexCount * 3);
	}

	public float[] normals() {
		return Arrays.copyOf(normals, vertexCount * 3);
	}

	public float[] texCoords() {
		return Arrays.copyOf(texCoords, vertexCount * 2);
	}

	public int[] indices() {
		return Arrays.copyOf(indices, indexCount);
	}

	private static float[] grow(float[] array, int needed) {
		return array.length >= needed ? array : Arrays.copyOf(array, Math.max(needed, array.length * 2));
	}

	private static int[] grow(int[] array, int needed) {
		return array.length >= needed ? array : Arrays.copyOf(array, Math.max(needed, array.length * 2));
	}
}
