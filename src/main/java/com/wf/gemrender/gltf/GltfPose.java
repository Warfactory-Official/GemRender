package com.wf.gemrender.gltf;

import java.util.Arrays;
import java.util.List;

import org.joml.Matrix4f;

import com.wf.gemrender.gltf.morph.GltfMorphLayout;

public final class GltfPose {
	public static final class Scratch {
		private final Matrix4f local = new Matrix4f();

		private float[] state = new float[0];
		private Matrix4f[] palette = new Matrix4f[0];
		private float[] morphBlock = new float[0];

		public Matrix4f[] palette(int size) {
			if (palette.length < size) {
				int grown = palette.length;
				palette = Arrays.copyOf(palette, size);
				for (int i = grown; i < size; i++) {
					palette[i] = new Matrix4f();
				}
			}
			return palette;
		}

		public float[] morphBlock(int floats) {
			if (morphBlock.length < floats) {
				morphBlock = new float[floats];
			}
			return morphBlock;
		}

		private float[] state(NodeTable table) {
			if (state.length < table.scratchFloats()) {
				state = new float[table.scratchFloats()];
			}
			table.resetToRest(state);
			return state;
		}
	}

	private GltfPose() {
	}

	public static void evaluate(GltfPaletteLayout layout, GltfAnimation animation, float timeSeconds,
			Matrix4f[] palette) {
		evaluate(layout, animation, timeSeconds, palette, GltfMorphLayout.NONE, null);
	}

	public static void evaluate(GltfPaletteLayout layout, GltfAnimation animation, float timeSeconds,
			Matrix4f[] palette, GltfMorphLayout morphs, float[] morphOut) {
		evaluate(layout, animation, timeSeconds, palette, morphs, morphOut, new Scratch());
	}

	public static void evaluate(GltfPaletteLayout layout, GltfAnimation animation, float timeSeconds,
			Matrix4f[] palette, GltfMorphLayout morphs, float[] morphOut, Scratch scratch) {
		NodeTable table = layout.nodeTable();

		float[] state = scratch.state(table);

		if (animation != null) {
			animation.apply(timeSeconds, state);
		}

		compose(layout, palette, morphs, morphOut, scratch, state);
	}

	/**
	 * Several clips at once, each at its own instant, applied in order onto one pose. The skinned
	 * counterpart of {@link PartsPose#evaluate(GemRenderPartsModel, GltfAnimation[], float[], Matrix4f[],
	 * boolean[], Scratch)}, and the only way a copy can carry two animations that answer to different
	 * things -- a mob that walks on distance travelled and bites on an attack timer.
	 */
	public static void evaluate(GltfPaletteLayout layout, GltfAnimation[] animations, float[] times,
			Matrix4f[] palette, GltfMorphLayout morphs, float[] morphOut, Scratch scratch) {
		NodeTable table = layout.nodeTable();

		float[] state = scratch.state(table);

		for (int layer = 0; layer < animations.length; layer++) {
			if (animations[layer] != null) {
				animations[layer].apply(times[layer], state);
			}
		}

		compose(layout, palette, morphs, morphOut, scratch, state);
	}

	private static void compose(GltfPaletteLayout layout, Matrix4f[] palette, GltfMorphLayout morphs,
			float[] morphOut, Scratch scratch, float[] state) {
		NodeTable table = layout.nodeTable();

		int[] order = table.evaluationOrder();
		int[] parents = table.parentSlots();
		Matrix4f local = scratch.local;

		for (int k = 0; k < order.length; k++) {
			int slot = order[k];
			int parent = parents[slot];

			if (parent < 0) {
				table.localTransform(state, slot, palette[slot]);
			} else {
				table.localTransform(state, slot, local);
				palette[slot].set(palette[parent])
						.mul(local);
			}
		}

		List<GltfPaletteLayout.SkinBlock> skins = layout.skins();
		for (int s = 0; s < skins.size(); s++) {
			GltfPaletteLayout.SkinBlock block = skins.get(s);
			int[] jointSlots = block.jointSlots();
			for (int j = 0; j < jointSlots.length; j++) {
				palette[block.base() + j].set(palette[jointSlots[j]])
						.mul(block.inverseBind()[j]);
			}
		}

		if (!morphs.isEmpty() && morphOut != null) {
			morphs.writeBlock(morphOut, state);
		}
	}
}
