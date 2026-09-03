package com.wf.gemrender.vendor.mcgltf.animation;

public class LinearInterpolatedChannel extends InterpolatedChannel {

	/**
	 * The values. Each element of this array corresponds to one key
	 * frame time
	 */
	protected final float[][] values;

	public LinearInterpolatedChannel(float[] timesS, float[][] values) {
		super(timesS);
		this.values = values;
	}

	@Override
	public void sample(float timeS, float[] out, int offset, int count) {
		if(timeS <= timesS[0]) {
			System.arraycopy(values[0], 0, out, offset, count);
		}
		else if(timeS >= timesS[timesS.length - 1]) {
			System.arraycopy(values[timesS.length - 1], 0, out, offset, count);
		}
		else {
			int previousIndex = computeIndex(timeS, timesS);
			int nextIndex = previousIndex + 1;

			float local = timeS - timesS[previousIndex];
			float delta = timesS[nextIndex] - timesS[previousIndex];
			//Duplicate timestamps (exporters emulating STEP) give delta == 0; 0/0 is NaN, which would
			//propagate into the pose and make the whole subtree vanish for the session.
			float alpha = delta > 0.0F ? local / delta : 0.0F;

			float[] previousPoint = values[previousIndex];
			float[] nextPoint = values[nextIndex];

			for(int i = 0; i < count; i++) {
				float p = previousPoint[i];
				float n = nextPoint[i];
				out[offset + i] = p + alpha * (n - p);
			}
		}
	}

}
