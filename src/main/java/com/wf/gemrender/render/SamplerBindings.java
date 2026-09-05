package com.wf.gemrender.render;

import com.wf.gemrender.GemRender;
import com.wf.gemrender.particle.ParticleBuffer;
import com.wf.gemrender.volume.SceneDepth;
import com.wf.gemrender.volume.VolumeAtlas;
import com.wf.gemrender.volume.VolumeBuffer;
import com.wf.gemrender.volume.VolumeNoise;

import dev.engine_room.flywheel.backend.gl.shader.GlProgram;

public final class SamplerBindings {
	private SamplerBindings() {
	}

	private static boolean logged;

	public static void apply(GlProgram program) {
		program.setSamplerBinding("_gemrender_bones", BoneBuffer.TEXTURE_UNIT);
		program.setSamplerBinding("_gemrender_morphs", MorphBuffer.TEXTURE_UNIT);
		program.setSamplerBinding("_gemrender_particles", ParticleBuffer.TEXTURE_UNIT);
		program.setSamplerBinding("_gemrender_volumes", VolumeBuffer.TEXTURE_UNIT);
		program.setSamplerBinding("_gemrender_volumeNoise", VolumeNoise.TEXTURE_UNIT);
		program.setSamplerBinding("_gemrender_sceneDepth", SceneDepth.TEXTURE_UNIT);
		program.setSamplerBinding("_gemrender_volumeField", VolumeAtlas.TEXTURE_UNIT);

		if (!logged) {
			int bones = program.getUniformLocation("_gemrender_bones");
			if (bones >= 0) {
				logged = true;
				GemRender.LOGGER.info("Sampler bindings applied: _gemrender_bones -> unit {}, "
						+ "_gemrender_morphs -> unit {}", BoneBuffer.TEXTURE_UNIT, MorphBuffer.TEXTURE_UNIT);
			}
		}
	}
}
