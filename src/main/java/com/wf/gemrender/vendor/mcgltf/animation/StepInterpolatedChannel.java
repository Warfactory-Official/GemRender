package com.wf.gemrender.vendor.mcgltf.animation;

public class StepInterpolatedChannel extends InterpolatedChannel {

	/**
	 * The values. Each element of this array corresponds to one key
	 * frame time
	 */
	protected final float[][] values;

	public StepInterpolatedChannel(float[] timesS, float[][] values) {
		super(timesS);
		this.values = values;
	}

	/**
	 * @param rotation whether these keyframes are quaternions, and should be normalized once here rather
	 *                 than on every read. GEMRENDER; see {@link InterpolatedChannel#normalize}.
	 */
	public StepInterpolatedChannel(float[] timesS, float[][] values, boolean rotation) {
		this(timesS, values);
		if (rotation) {
			normalizeKeyframes(values);
		}
	}

	@Override
	public void sample(float timeS, float[] out, int offset, int count) {
		System.arraycopy(values[computeIndex(timeS, timesS)], 0, out, offset, count);
	}

}
