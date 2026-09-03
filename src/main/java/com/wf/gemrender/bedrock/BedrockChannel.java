package com.wf.gemrender.bedrock;

import com.wf.gemrender.gltf.PoseDriver;

public sealed interface BedrockChannel extends PoseDriver {
	float UNITS_PER_BLOCK = 16.0f;

	BedrockTrack track();

	@Override
	default float cycleSeconds() {
		return track().lastKeyTime();
	}

	record Position(BedrockTrack track, int offset) implements BedrockChannel {
		@Override
		public void apply(float timeSeconds, float[] scratch) {
			float restX = scratch[offset];
			float restY = scratch[offset + 1];
			float restZ = scratch[offset + 2];

			track.sample(timeSeconds, scratch, offset);

			scratch[offset] = restX - scratch[offset] / UNITS_PER_BLOCK;
			scratch[offset + 1] = restY + scratch[offset + 1] / UNITS_PER_BLOCK;
			scratch[offset + 2] = restZ + scratch[offset + 2] / UNITS_PER_BLOCK;
		}
	}

	record Rotation(BedrockTrack track, int offset, float restX, float restY, float restZ)
			implements BedrockChannel {
		@Override
		public void apply(float timeSeconds, float[] scratch) {
			track.sample(timeSeconds, scratch, offset);

			float x = restX + (float) Math.toRadians(-scratch[offset]);
			float y = restY + (float) Math.toRadians(-scratch[offset + 1]);
			float z = restZ + (float) Math.toRadians(scratch[offset + 2]);

			BedrockSkeleton.quaternion(x, y, z, scratch, offset);
		}
	}

	record Scale(BedrockTrack track, int offset) implements BedrockChannel {
		@Override
		public void apply(float timeSeconds, float[] scratch) {
			float restX = scratch[offset];
			float restY = scratch[offset + 1];
			float restZ = scratch[offset + 2];

			track.sample(timeSeconds, scratch, offset);

			scratch[offset] *= restX;
			scratch[offset + 1] *= restY;
			scratch[offset + 2] *= restZ;
		}
	}
}
