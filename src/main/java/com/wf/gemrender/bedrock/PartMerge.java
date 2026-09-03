package com.wf.gemrender.bedrock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.wf.gemrender.gltf.MeshGeometry;

public final class PartMerge {
	public interface Pixels {
		int width();

		int height();

		int argb(int x, int y);
	}

	private static final int QUAD_VERTICES = 4;

	private static final float UV_EPSILON = 1.0e-5f;

	private static final float PIXEL_EPSILON = 1.0e-3f;

	private PartMerge() {
	}

	public static int[] canonical(List<@Nullable MeshGeometry> meshes, @Nullable Pixels texture) {
		int[] out = new int[meshes.size()];
		Map<Long, List<Integer>> byShape = new LinkedHashMap<>();

		for (int i = 0; i < meshes.size(); i++) {
			out[i] = i;
			MeshGeometry mesh = meshes.get(i);
			if (mesh == null) {
				continue;
			}

			List<Integer> candidates = byShape.computeIfAbsent(shapeHash(mesh), key -> new ArrayList<>());
			for (int candidate : candidates) {
				if (matches(meshes.get(candidate), mesh, texture)) {
					out[i] = candidate;
					break;
				}
			}
			if (out[i] == i) {
				candidates.add(i);
			}
		}

		return out;
	}

	public static boolean matches(MeshGeometry a, MeshGeometry b, @Nullable Pixels texture) {
		if (!sameGeometry(a, b)) {
			return false;
		}
		return texture == null ? sameUvs(a, b) : samePixels(a, b, texture);
	}

	private static boolean sameGeometry(MeshGeometry a, MeshGeometry b) {
		if (a.vertexCount() != b.vertexCount() || a.indexCount() != b.indexCount()) {
			return false;
		}
		for (int i = 0; i < a.indexCount(); i++) {
			if (a.index(i) != b.index(i)) {
				return false;
			}
		}
		for (int v = 0; v < a.vertexCount(); v++) {
			for (int axis = 0; axis < 3; axis++) {
				if (a.position(v, axis) != b.position(v, axis) || a.normal(v, axis) != b.normal(v, axis)) {
					return false;
				}
			}
		}
		return true;
	}

	private static boolean sameUvs(MeshGeometry a, MeshGeometry b) {
		for (int v = 0; v < a.vertexCount(); v++) {
			if (a.texCoord(v, 0) != b.texCoord(v, 0) || a.texCoord(v, 1) != b.texCoord(v, 1)) {
				return false;
			}
		}
		return true;
	}

	private static boolean samePixels(MeshGeometry a, MeshGeometry b, Pixels texture) {
		if (a.vertexCount() % QUAD_VERTICES != 0) {
			return sameUvs(a, b);
		}

		for (int quad = 0; quad * QUAD_VERTICES < a.vertexCount(); quad++) {
			int base = quad * QUAD_VERTICES;

			float du = b.texCoord(base, 0) - a.texCoord(base, 0);
			float dv = b.texCoord(base, 1) - a.texCoord(base, 1);

			for (int i = 1; i < QUAD_VERTICES; i++) {
				if (Math.abs(b.texCoord(base + i, 0) - a.texCoord(base + i, 0) - du) > UV_EPSILON
						|| Math.abs(b.texCoord(base + i, 1) - a.texCoord(base + i, 1) - dv) > UV_EPSILON) {
					return false;
				}
			}

			float shiftX = du * texture.width();
			float shiftY = dv * texture.height();
			int dx = Math.round(shiftX);
			int dy = Math.round(shiftY);
			if (Math.abs(shiftX - dx) > PIXEL_EPSILON || Math.abs(shiftY - dy) > PIXEL_EPSILON) {
				return false;
			}
			if (dx == 0 && dy == 0) {
				continue;
			}

			if (!sameRectangle(a, base, texture, dx, dy)) {
				return false;
			}
		}

		return true;
	}

	private static boolean sameRectangle(MeshGeometry mesh, int base, Pixels texture, int dx, int dy) {
		int x0 = texture.width();
		int x1 = 0;
		int y0 = texture.height();
		int y1 = 0;

		for (int i = 0; i < QUAD_VERTICES; i++) {
			int x = Math.round(mesh.texCoord(base + i, 0) * texture.width());
			int y = Math.round(mesh.texCoord(base + i, 1) * texture.height());
			x0 = Math.min(x0, x);
			x1 = Math.max(x1, x);
			y0 = Math.min(y0, y);
			y1 = Math.max(y1, y);
		}

		if (x0 < 0 || y0 < 0 || x1 > texture.width() || y1 > texture.height()) {
			return false;
		}
		if (x0 + dx < 0 || y0 + dy < 0 || x1 + dx > texture.width() || y1 + dy > texture.height()) {
			return false;
		}

		for (int y = y0; y < y1; y++) {
			for (int x = x0; x < x1; x++) {
				if (!samePixel(texture.argb(x, y), texture.argb(x + dx, y + dy))) {
					return false;
				}
			}
		}
		return true;
	}

	private static boolean samePixel(int left, int right) {
		if (left == right) {
			return true;
		}
		return (left >>> 24) == 0 && (right >>> 24) == 0;
	}

	private static long shapeHash(MeshGeometry mesh) {
		long hash = mesh.vertexCount() * 31L + mesh.indexCount();
		for (int v = 0; v < mesh.vertexCount(); v++) {
			for (int axis = 0; axis < 3; axis++) {
				hash = hash * 31 + Float.floatToIntBits(mesh.position(v, axis));
				hash = hash * 31 + Float.floatToIntBits(mesh.normal(v, axis));
			}
		}
		return hash;
	}
}
