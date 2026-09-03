package com.wf.gemrender.rig;

import org.jetbrains.annotations.Nullable;

/**
 * One mesh on its way into a rig: positions, optional normals, texture coordinates and a triangle index
 * list, in the frame the artist authored them in.
 *
 * <p>Deliberately plain arrays rather than a mesh type. Where these come from is not GemRender's
 * business -- {@link WavefrontObj} is one source, and a mod that generates geometry or reads a format of
 * its own is another -- and everything they need to carry to be skinned rigidly is here.
 *
 * <p>Positions and normals are three floats a vertex, texture coordinates two, in the vertex order the
 * indices refer to. Normals may be null, in which case every vertex is given the +Y one Flywheel uses
 * for an unlit surface.
 */
public record RigGeometry(float[] positions, @Nullable float[] normals, float[] texCoords, int[] indices) {
	public RigGeometry {
		if (positions.length % 3 != 0) {
			throw new IllegalArgumentException("positions must be 3 floats a vertex, got " + positions.length);
		}

		int vertices = positions.length / 3;
		if (normals != null && normals.length != vertices * 3) {
			throw new IllegalArgumentException("mesh has " + vertices + " vertices but " + normals.length / 3
					+ " normals");
		}
		if (texCoords.length != vertices * 2) {
			throw new IllegalArgumentException("mesh has " + vertices + " vertices but " + texCoords.length / 2
					+ " texture coordinates");
		}
		for (int index : indices) {
			if (index < 0 || index >= vertices) {
				throw new IllegalArgumentException("index " + index + " is outside the mesh's " + vertices
						+ " vertices");
			}
		}
	}

	public int vertexCount() {
		return positions.length / 3;
	}
}
