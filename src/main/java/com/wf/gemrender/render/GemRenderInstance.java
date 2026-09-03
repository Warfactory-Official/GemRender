package com.wf.gemrender.render;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.joml.Vector4fc;

import com.wf.gemrender.gltf.skin.SkinnedBounds;

import dev.engine_room.flywheel.api.instance.InstanceHandle;
import dev.engine_room.flywheel.api.instance.InstanceType;
import dev.engine_room.flywheel.lib.instance.ColoredLitInstance;

/**
 * The instance a visual writes: {@code pose}, {@code boneBase}, {@code morphBase}, {@code boneSphere}.
 *
 * <p>Leaving {@code boneSphere} at its default culls the geometry away without an error.
 */
public class GemRenderInstance extends ColoredLitInstance {
	public final Matrix4f pose = new Matrix4f();

	public int boneBase = 0;

	public int morphBase = 0;

	public final Vector4f boneSphere = new Vector4f(0.0f, 0.0f, 0.0f, 1.0f);

	public GemRenderInstance(InstanceType<? extends GemRenderInstance> type, InstanceHandle handle) {
		super(type, handle);
	}

	public GemRenderInstance boneBase(int boneBase) {
		this.boneBase = boneBase;
		return this;
	}

	public GemRenderInstance boneSphere(Vector4fc sphere) {
		this.boneSphere.set(sphere);
		return this;
	}

	public GemRenderInstance setPose(Matrix4f pose) {
		this.pose.set(pose);
		return this;
	}

	/**
	 * Draws nothing, without giving the instance up.
	 *
	 * <p>For a pool: a crowd whose size changes every few seconds would churn the instancer's buffers if
	 * it created and deleted instances to match, so the surplus is collapsed instead and reused when the
	 * crowd grows again. The counterpart of Flywheel's {@code TransformedInstance.setZeroTransform}, and
	 * it has to zero the bounding sphere as well: geometry that collapses to a point still costs a
	 * vertex shader run per vertex unless the culling pass throws the instance away first.
	 */
	public GemRenderInstance setZeroTransform() {
		pose.zero();
		boneSphere.set(0.0f, 0.0f, 0.0f, 0.0f);
		return this;
	}
}
