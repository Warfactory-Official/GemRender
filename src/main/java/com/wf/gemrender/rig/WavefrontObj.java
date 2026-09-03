package com.wf.gemrender.rig;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.wf.gemrender.GemRender;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * Reads a Wavefront {@code .obj} as one {@link RigGeometry} per named {@code o}/{@code g} group.
 *
 * <p>The bridge between the models a lot of mods already ship and {@link RigBuilder}. An obj has no
 * skeleton, which is exactly the gap the builder fills: the file says what the parts <em>are</em> and
 * the rig says how they hang together, so an existing set of part meshes becomes a rigged asset without
 * being re-exported.
 *
 * <p>Two conventions, both matching what NeoForge's own obj loader does with {@code flip_v: true}:
 * the V axis is flipped, because obj measures it up from the bottom left and Minecraft measures it down
 * from the top left; and faces are fan-triangulated, so quads and n-gons are all fine.
 *
 * <p>Materials are ignored. An obj's {@code .mtl} names a texture GemRender has no way to resolve --
 * in practice it is a placeholder the model json fills in -- so the texture belongs in the
 * {@link com.wf.gemrender.gltf.GltfMaterial} handed to {@link RigBuilder#build}.
 */
public final class WavefrontObj {
	private WavefrontObj() {
	}

	/**
	 * @return the file's named groups, in file order. A file with no {@code o} or {@code g} line comes
	 *         back as a single group named {@code default}.
	 * @throws IOException if the resource is missing or unreadable
	 */
	public static Map<String, RigGeometry> load(ResourceLocation obj) throws IOException {
		Floats positions = new Floats();
		Floats texCoords = new Floats();
		Floats normals = new Floats();

		Map<String, RigGeometry> groups = new LinkedHashMap<>();
		Group group = new Group("default");

		try (BufferedReader reader = Minecraft.getInstance()
				.getResourceManager()
				.getResourceOrThrow(obj)
				.openAsReader()) {
			String line;
			int number = 0;
			while ((line = reader.readLine()) != null) {
				number++;
				line = line.trim();
				if (line.isEmpty() || line.charAt(0) == '#') {
					continue;
				}

				try {
					if (line.startsWith("v ")) {
						String[] tokens = split(line);
						positions.add(number(tokens, 1))
								.add(number(tokens, 2))
								.add(number(tokens, 3));
					} else if (line.startsWith("vt ")) {
						String[] tokens = split(line);
						texCoords.add(number(tokens, 1))
								.add(1.0f - number(tokens, 2));
					} else if (line.startsWith("vn ")) {
						String[] tokens = split(line);
						normals.add(number(tokens, 1))
								.add(number(tokens, 2))
								.add(number(tokens, 3));
					} else if (line.startsWith("f ")) {
						group.face(split(line), positions, texCoords, normals);
					} else if (line.startsWith("o ") || line.startsWith("g ")) {
						group.flush(groups);
						group = new Group(line.substring(2)
								.trim());
					}
				} catch (RuntimeException e) {
					throw new IOException(obj + " line " + number + ": " + line, e);
				}
			}
		}
		group.flush(groups);

		if (groups.isEmpty()) {
			throw new IOException(obj + " has no faces");
		}

		GemRender.LOGGER.debug("Read obj {}: {} group(s) {}", obj, groups.size(), groups.keySet());
		return groups;
	}

	/**
	 * One group being accumulated. Corners are deduplicated as they are read, so a cube shared between
	 * six faces is eight vertices rather than twenty-four and the index buffer does the sharing.
	 */
	private static final class Group {
		private final String name;

		private final Map<String, Integer> byCorner = new HashMap<>();
		private final Floats positions = new Floats();
		private final Floats normals = new Floats();
		private final Floats texCoords = new Floats();
		private final List<Integer> indices = new ArrayList<>();

		private boolean anyNormals;

		private Group(String name) {
			this.name = name;
		}

		private void face(String[] tokens, Floats sourcePositions, Floats sourceTexCoords,
				Floats sourceNormals) {
			for (int k = 2; k + 1 < tokens.length; k++) {
				indices.add(corner(tokens[1], sourcePositions, sourceTexCoords, sourceNormals));
				indices.add(corner(tokens[k], sourcePositions, sourceTexCoords, sourceNormals));
				indices.add(corner(tokens[k + 1], sourcePositions, sourceTexCoords, sourceNormals));
			}
		}

		private int corner(String token, Floats sourcePositions, Floats sourceTexCoords,
				Floats sourceNormals) {
			Integer existing = byCorner.get(token);
			if (existing != null) {
				return existing;
			}

			int slash = token.indexOf('/');
			int second = slash < 0 ? -1 : token.indexOf('/', slash + 1);
			String vertex = slash < 0 ? token : token.substring(0, slash);
			String texture = slash < 0 || second == slash + 1 ? ""
					: token.substring(slash + 1, second < 0 ? token.length() : second);
			String normal = second < 0 ? "" : token.substring(second + 1);

			int p = deref(vertex, sourcePositions.size() / 3) * 3;
			positions.add(sourcePositions.get(p))
					.add(sourcePositions.get(p + 1))
					.add(sourcePositions.get(p + 2));

			if (texture.isEmpty()) {
				texCoords.add(0.0f)
						.add(0.0f);
			} else {
				int t = deref(texture, sourceTexCoords.size() / 2) * 2;
				texCoords.add(sourceTexCoords.get(t))
						.add(sourceTexCoords.get(t + 1));
			}

			if (normal.isEmpty()) {
				normals.add(0.0f)
						.add(1.0f)
						.add(0.0f);
			} else {
				anyNormals = true;
				int n = deref(normal, sourceNormals.size() / 3) * 3;
				normals.add(sourceNormals.get(n))
						.add(sourceNormals.get(n + 1))
						.add(sourceNormals.get(n + 2));
			}

			int index = positions.size() / 3 - 1;
			byCorner.put(token, index);
			return index;
		}

		private void flush(Map<String, RigGeometry> groups) {
			if (indices.isEmpty()) {
				return;
			}

			int[] packed = new int[indices.size()];
			for (int i = 0; i < packed.length; i++) {
				packed[i] = indices.get(i);
			}

			groups.put(name, new RigGeometry(positions.toArray(), anyNormals ? normals.toArray() : null,
					texCoords.toArray(), packed));
		}
	}

	/** Obj indices are one-based, and a negative one counts back from what has been declared so far. */
	private static int deref(String token, int count) {
		int index = Integer.parseInt(token);
		return index > 0 ? index - 1 : count + index;
	}

	private static String[] split(String line) {
		return line.split("\\s+");
	}

	private static float number(String[] tokens, int index) {
		return Float.parseFloat(tokens[index]);
	}

	/** A growable float array; the parse touches every number in the file and {@code List} would box it. */
	private static final class Floats {
		private float[] data = new float[1024];
		private int size;

		private Floats add(float value) {
			if (size == data.length) {
				data = java.util.Arrays.copyOf(data, size * 2);
			}
			data[size++] = value;
			return this;
		}

		private float get(int index) {
			return data[index];
		}

		private int size() {
			return size;
		}

		private float[] toArray() {
			return java.util.Arrays.copyOf(data, size);
		}
	}
}
