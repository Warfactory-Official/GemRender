package com.wf.gemrender.gltf;

import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector4fc;

import dev.engine_room.flywheel.api.model.Model;
import net.minecraft.resources.ResourceLocation;

/**
 * An asset split into rigid parts: one Flywheel {@link Model} per part, each drawn by one instance
 * carrying one transform.
 *
 * <p>The alternative to {@link GemRenderGltfModel}, for assets the game moves rather than a clip. A
 * skinned copy rebuilds its whole bone palette every frame whether or not anything moved, and two
 * copies only share that work when they land on the same instant; a vehicle whose turret is aimed by
 * its driver never does. Parts invert it: the parts that did not move upload nothing, and every copy
 * of the asset in the level shares one instancer per part, so the draw count is a property of the
 * model rather than of how many are on screen.
 */
public record GemRenderPartsModel(List<Part> parts, GltfPaletteLayout layout, int[] slotToPart,
		Map<String, GltfAnimation> animations, Vector4fc boundingSphere, int meshCount,
		List<ResourceLocation> textures) {
	/**
	 * One rigid subtree. {@code toParent} is the rest chain from the parent part's driving bone down to
	 * this one's, which by construction never animates, so {@code rootSlot} is the only bone that has
	 * to be evaluated. {@code model} is null for a part that links transforms but carries no geometry.
	 */
	public record Part(String name, int rootSlot, int parent, Matrix4f toParent, @Nullable Model model) {
	}

	public GemRenderPartsModel {
		parts = List.copyOf(parts);
		textures = List.copyOf(textures);
	}

	public int partCount() {
		return parts.size();
	}

	public GltfAnimation animation(String name) {
		return animations.get(name);
	}

	public GltfAnimation animationOrAny(String name) {
		GltfAnimation named = animations.get(name);
		if (named != null) {
			return named;
		}
		return animations.isEmpty() ? null : animations.values()
				.iterator()
				.next();
	}

	public Matrix4f[] newTransforms() {
		Matrix4f[] out = new Matrix4f[parts.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = new Matrix4f();
		}
		return out;
	}

	/**
	 * Which parts {@code clip} can actually move. This is the whole point of the path: a part the clip
	 * cannot reach holds the transform it was given once and is never evaluated, composed, compared or
	 * uploaded again, however many copies are on screen.
	 *
	 * <p>A driver that will not say where it writes forces every part on, because guessing wrong here
	 * freezes geometry that should be moving.
	 */
	public boolean[] drivenBy(@Nullable GltfAnimation clip) {
		boolean[] driven = new boolean[parts.size()];
		if (clip == null) {
			return driven;
		}

		NodeTable table = layout.nodeTable();
		for (PoseDriver driver : clip.drivers()) {
			int slot = table.slotOfOffset(driver.offset());
			if (slot < 0 || slot >= slotToPart.length) {
				java.util.Arrays.fill(driven, true);
				return driven;
			}
			driven[slotToPart[slot]] = true;
		}

		for (int i = 0; i < parts.size(); i++) {
			int parent = parts.get(i)
					.parent();
			if (parent >= 0 && driven[parent]) {
				driven[i] = true;
			}
		}
		return driven;
	}

	/**
	 * {@code driven} widened to include every part above a driven one. Placing a part reads its parent's
	 * transform, so when one layer of animation is re-evaluated on its own the chain down to it has to
	 * be re-evaluated with it, or the parent's slot still holds whatever copy touched it last.
	 *
	 * <p>Parts come out of the partition in topological order, so walking backwards carries a part's
	 * need for its parent all the way to the root in one pass.
	 */
	public boolean[] withAncestors(boolean[] driven) {
		boolean[] out = driven.clone();
		for (int i = out.length - 1; i >= 0; i--) {
			int parent = parts.get(i)
					.parent();
			if (out[i] && parent >= 0) {
				out[parent] = true;
			}
		}
		return out;
	}

	public static int countTrue(boolean[] flags) {
		int count = 0;
		for (boolean flag : flags) {
			if (flag) {
				count++;
			}
		}
		return count;
	}
}
