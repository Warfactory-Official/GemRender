package com.wf.gemrender.render;

import com.wf.gemrender.GemRender;
import com.wf.gemrender.particle.ParticleBuffer;
import com.wf.gemrender.texture.PaintArray;
import com.wf.gemrender.volume.SceneDepth;
import com.wf.gemrender.volume.VolumeAtlas;
import com.wf.gemrender.volume.VolumeBuffer;
import com.wf.gemrender.volume.VolumeNoise;

import dev.engine_room.flywheel.backend.gl.shader.GlProgram;

//? if >=26.2 {
/*import static org.lwjgl.opengl.GL20C.GL_CURRENT_PROGRAM;
import static org.lwjgl.opengl.GL20C.glGetInteger;
import static org.lwjgl.opengl.GL20C.glGetUniformLocation;
import static org.lwjgl.opengl.GL20C.glUniform1i;
import static org.lwjgl.opengl.GL20C.glUseProgram;
*///?}

public final class SamplerBindings {
    private static boolean logged;

    private SamplerBindings() {
    }

    public static int[] units() {
        return new int[] {BoneBuffer.TEXTURE_UNIT, MorphBuffer.TEXTURE_UNIT, ParticleBuffer.TEXTURE_UNIT,
                VolumeBuffer.TEXTURE_UNIT, VolumeNoise.TEXTURE_UNIT, SceneDepth.TEXTURE_UNIT,
                VolumeAtlas.TEXTURE_UNIT, PaintArray.TEXTURE_UNIT, TextureUnits.ABSORBANCE_BAND};
    }

    //? if >=26.2 {
    /*private static final String[] NAMES = {"_gemrender_bones", "_gemrender_morphs", "_gemrender_particles",
            "_gemrender_volumes", "_gemrender_volumeNoise", "_gemrender_sceneDepth", "_gemrender_volumeField",
            "_gemrender_paint"};

    /^* {@link #units()} order = {@link #NAMES} order. ^/
    public static void apply(int program) {
        int[] units = units();
        int previous = -1;
        for (int i = 0; i < NAMES.length; i++) {
            int location = glGetUniformLocation(program, NAMES[i]);
            if (location < 0) {
                continue;
            }
            if (previous < 0) {
                previous = glGetInteger(GL_CURRENT_PROGRAM);
                glUseProgram(program);
            }
            glUniform1i(location, units[i]);
        }
        if (previous >= 0) {
            glUseProgram(previous);
        }
    }

    *///?}

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
