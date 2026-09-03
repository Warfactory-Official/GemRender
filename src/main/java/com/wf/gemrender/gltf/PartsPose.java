package com.wf.gemrender.gltf;

import java.util.List;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import com.wf.gemrender.gltf.GemRenderPartsModel.Part;

/** {@link GltfPose} for the rigid-part path: one transform per part rather than one per bone. */
public final class PartsPose {
	/** The working buffers {@link #evaluate} needs. Hold one per thread; it is not thread-safe. */
	public static final class Scratch {
		private final Matrix4f local = new Matrix4f();
		private float[] state = new float[0];

		private float[] state(NodeTable table) {
			if (state.length < table.scratchFloats()) {
				state = new float[table.scratchFloats()];
			}
			table.resetToRest(state);
			return state;
		}
	}

	private PartsPose() {
	}

	/**
	 * Writes each part's transform relative to the model's root into {@code out}, which must be at
	 * least {@code model.partCount()} long. Parts come out of the partition in topological order, so
	 * one forward pass is enough.
	 */
	public static void evaluate(GemRenderPartsModel model, GltfAnimation animation, float timeSeconds,
			Matrix4f[] out, Scratch scratch) {
		evaluate(model, animation, timeSeconds, out, null, scratch);
	}

	/**
	 * As above, but recomputing only the parts {@code only} marks. The rest keep whatever {@code out}
	 * already holds, which must be the transform they would have got anyway; see
	 * {@link GemRenderPartsModel#drivenBy}, whose result is the only safe thing to pass here.
	 */
	public static void evaluate(GemRenderPartsModel model, GltfAnimation animation, float timeSeconds,
			Matrix4f[] out, boolean @Nullable [] only, Scratch scratch) {
		NodeTable table = model.layout()
				.nodeTable();
		float[] state = scratch.state(table);

		if (animation != null) {
			animation.apply(timeSeconds, state);
		}

		compose(model, out, only, scratch, state);
	}

	/**
	 * Several clips at once, each at its own instant. Layers are applied in order onto one pose, which
	 * is what makes a turret aimed by its driver and a tread scrolling on a loop cost one evaluation
	 * between them rather than one each.
	 *
	 * <p>{@code only} must cover every part the changed layers can move <em>and</em> everything above
	 * them; see {@link GemRenderPartsModel#withAncestors}.
	 */
	public static void evaluate(GemRenderPartsModel model, GltfAnimation[] clips, float[] times,
			Matrix4f[] out, boolean @Nullable [] only, Scratch scratch) {
		NodeTable table = model.layout()
				.nodeTable();
		float[] state = scratch.state(table);

		for (int layer = 0; layer < clips.length; layer++) {
			if (clips[layer] != null) {
				clips[layer].apply(times[layer], state);
			}
		}

		compose(model, out, only, scratch, state);
	}

	private static void compose(GemRenderPartsModel model, Matrix4f[] out, boolean @Nullable [] only,
			Scratch scratch, float[] state) {
		NodeTable table = model.layout()
				.nodeTable();
		List<Part> parts = model.parts();
		Matrix4f local = scratch.local;

		for (int i = 0; i < parts.size(); i++) {
			if (only != null && !only[i]) {
				continue;
			}

			Part part = parts.get(i);
			table.localTransform(state, part.rootSlot(), local);

			if (part.parent() < 0) {
				out[i].set(part.toParent())
						.mul(local);
			} else {
				out[i].set(out[part.parent()])
						.mul(part.toParent())
						.mul(local);
			}
		}
	}
}
