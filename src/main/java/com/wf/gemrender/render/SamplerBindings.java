package com.wf.gemrender.render;

import com.wf.gemrender.GemRender;
import com.wf.gemrender.particle.ParticleBuffer;
import com.wf.gemrender.texture.PaintArray;
import com.wf.gemrender.volume.SceneDepth;
import com.wf.gemrender.volume.VolumeAtlas;
import com.wf.gemrender.volume.VolumeBuffer;
import com.wf.gemrender.volume.VolumeNoise;

import dev.engine_room.flywheel.backend.gl.shader.GlProgram;


public final class SamplerBindings {
    private static boolean logged;

    private SamplerBindings() {
    }

    public static int[] units() {
        return new int[] {BoneBuffer.TEXTURE_UNIT, MorphBuffer.TEXTURE_UNIT, ParticleBuffer.TEXTURE_UNIT,
                VolumeBuffer.TEXTURE_UNIT, VolumeNoise.TEXTURE_UNIT, SceneDepth.TEXTURE_UNIT,
                VolumeAtlas.TEXTURE_UNIT, PaintArray.TEXTURE_UNIT, TextureUnits.ABSORBANCE_BAND};
    }


    public static void apply(GlProgram program) {
        program.setSamplerBinding("_gemrender_bones", BoneBuffer.TEXTURE_UNIT);
        program.setSamplerBinding("_gemrender_morphs", MorphBuffer.TEXTURE_UNIT);
        program.setSamplerBinding("_gemrender_particles", ParticleBuffer.TEXTURE_UNIT);
        program.setSamplerBinding("_gemrender_volumes", VolumeBuffer.TEXTURE_UNIT);
        program.setSamplerBinding("_gemrender_volumeNoise", VolumeNoise.TEXTURE_UNIT);
        program.setSamplerBinding("_gemrender_sceneDepth", SceneDepth.TEXTURE_UNIT);
        program.setSamplerBinding("_gemrender_volumeField", VolumeAtlas.TEXTURE_UNIT);
        program.setSamplerBinding("_gemrender_paint", PaintArray.TEXTURE_UNIT);
        program.setSamplerBinding("_gemrender_absorbanceBand", TextureUnits.ABSORBANCE_BAND);

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
