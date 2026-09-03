package com.wf.gemrender.vendor.mcgltf.animation;

public class SphericalLinearInterpolatedChannel extends InterpolatedChannel {

	/**
	 * The values. Each element of this array corresponds to one key
	 * frame time
	 */
	protected final float[][] values;

	public SphericalLinearInterpolatedChannel(float[] timesS, float[][] values) {
		super(timesS);
		this.values = values;
		//GEMRENDER: once here rather than on every read. Slerp of two unit quaternions is unit, so this is
		//also what lets the composer downstream skip its own normalization.
		normalizeKeyframes(values);
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

			// Adapted from javax.vecmath.Quat4f
			float ax = previousPoint[0];
			float ay = previousPoint[1];
			float az = previousPoint[2];
			float aw = previousPoint[3];
			float bx = nextPoint[0];
			float by = nextPoint[1];
			float bz = nextPoint[2];
			float bw = nextPoint[3];

			float dot = ax * bx + ay * by + az * bz + aw * bw;
			if (dot < 0)
			{
				bx = -bx;
				by = -by;
				bz = -bz;
				bw = -bw;
				dot = -dot;
			}
			float epsilon = 1e-6f;
			float s0, s1;
			if ((1.0 - dot) > epsilon)
			{
				float omega = (float)Math.acos(dot);
				float invSinOmega = 1.0f / (float)Math.sin(omega);
				s0 = (float)Math.sin((1.0 - alpha) * omega) * invSinOmega;
				s1 = (float)Math.sin(alpha * omega) * invSinOmega;
			}
			else
			{
				s0 = 1.0f - alpha;
				s1 = alpha;
			}
			float rx = s0 * ax + s1 * bx;
			float ry = s0 * ay + s1 * by;
			float rz = s0 * az + s1 * bz;
			float rw = s0 * aw + s1 * bw;

			//GEMRENDER: guarded by count rather than assuming four. A rotation channel is always VEC4, but
			//the destination decides how much of it is wanted, and a malformed file is not a reason to
			//write past the slot that was reserved.
			if(count > 0) out[offset] = rx;
			if(count > 1) out[offset + 1] = ry;
			if(count > 2) out[offset + 2] = rz;
			if(count > 3) out[offset + 3] = rw;
		}
	}

}
