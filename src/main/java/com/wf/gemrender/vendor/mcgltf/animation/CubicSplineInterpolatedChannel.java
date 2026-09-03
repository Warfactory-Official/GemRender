package com.wf.gemrender.vendor.mcgltf.animation;

public class CubicSplineInterpolatedChannel extends InterpolatedChannel {

	/**
	 * The values. Each element of this array corresponds to one key
	 * frame time
	 */
	protected final float[][][] values;

	/**
	 * Whether the sampled value is a quaternion and needs re-normalizing.
	 *
	 * <p>GEMRENDER: was an overridable {@code postProcessOutput} hook, which existed only so the creator
	 * could pick it out of an anonymous subclass. A flag now the channels are constructed directly.
	 */
	private final boolean rotation;

	public CubicSplineInterpolatedChannel(float[] timesS, float[][][] values) {
		this(timesS, values, false);
	}

	public CubicSplineInterpolatedChannel(float[] timesS, float[][][] values, boolean rotation) {
		super(timesS);
		this.values = values;
		this.rotation = rotation;
	}

	@Override
	public void sample(float timeS, float[] out, int offset, int count) {
		if(timeS <= timesS[0]) {
			System.arraycopy(values[0][1], 0, out, offset, count);
		}
		else if(timeS >= timesS[timesS.length - 1]) {
			System.arraycopy(values[timesS.length - 1][1], 0, out, offset, count);
		}
		else {
			// Adapted from https://github.khronos.org/glTF-Tutorials/gltfTutorial/gltfTutorial_007_Animations.html#cubic-spline-interpolation
			int previousIndex = computeIndex(timeS, timesS);
			int nextIndex = previousIndex + 1;

			float local = timeS - timesS[previousIndex];
			float delta = timesS[nextIndex] - timesS[previousIndex];
			//Duplicate timestamps (exporters emulating STEP) give delta == 0; 0/0 is NaN, which would
			//propagate into the pose and make the whole subtree vanish for the session.
			float alpha = delta > 0.0F ? local / delta : 0.0F;
			float alpha2 =  alpha * alpha;
			float alpha3 =  alpha2 * alpha;

			float aa = 2 * alpha3 - 3 * alpha2 + 1;
			float ab = alpha3 - 2 * alpha2 + alpha;
			float ac = -2 * alpha3 + 3 * alpha2;
			float ad = alpha3 - alpha2;

			float[][] previous = values[previousIndex];
			float[][] next = values[nextIndex];

			float[] previousPoint = previous[1];
			float[] nextPoint = next[1];
			float[] previousOutputTangent = previous[2];
			float[] nextInputTangent = next[0];

			for(int i = 0; i < count; i++) {
				float p = previousPoint[i];
				float pt = previousOutputTangent[i] * delta;
				float n = nextPoint[i];
				float nt = nextInputTangent[i] * delta;
				out[offset + i] = aa * p + ab * pt + ac * n + ad * nt;
			}
		}

		// glTF 2.0 Appendix C requires the Hermite-interpolated quaternion to be normalized before use
		if(rotation && count >= 4) {
			normalize(out, offset);
		}
	}

}
