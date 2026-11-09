package com.wf.gemrender.effect;

import com.wf.gemrender.gl.HeadlessGl;
import com.wf.gemrender.gl.ShaderSources;
import com.wf.gemrender.render.GlPrograms;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.lwjgl.opengl.GL33C.*;

@Tag("gl")
class SceneEffectsGlTest {
    @Test
    void scenePassPreservesAlphaAndInactiveColorAndStaysFiniteAtTheSource() {
        try (HeadlessGl ignored = HeadlessGl.createOrSkip()) {
            String base = "assets/gemrender/shaders/";
            int program = GlPrograms.link("scene-effects-test",
                    "#version 420 core\n" + ShaderSources.read(base + "water_split.vert"),
                    "#version 420 core\n" + ShaderSources.read(base + "scene_effects.frag"));
            int fbo = glGenFramebuffers(), texture = glGenTextures(), vao = glGenVertexArrays();
            try {
                glBindTexture(GL_TEXTURE_2D, texture);
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA32F, 32, 32, 0, GL_RGBA, GL_FLOAT, (java.nio.ByteBuffer) null);
                glBindFramebuffer(GL_FRAMEBUFFER, fbo);
                glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
                assertThat(glCheckFramebufferStatus(GL_FRAMEBUFFER)).isEqualTo(GL_FRAMEBUFFER_COMPLETE);
                glBindVertexArray(vao);
                glViewport(0, 0, 32, 32);
                glUseProgram(program);
                glUniformMatrix4fv(glGetUniformLocation(program, "_gr_inverse"), false,
                        new Matrix4f().perspective(1, 1, 0.05f, 1000).invert().get(new float[16]));
                glUniform2f(glGetUniformLocation(program, "_gr_size"), 32, 32);
                glUniform3f(glGetUniformLocation(program, "_gr_tint"), 0.3f, 0.7f, 1);
                glUniform1f(glGetUniformLocation(program, "_gr_time"), 4);
                glEnable(GL_BLEND);
                for (boolean active : new boolean[]{false, true}) {
                    for (float sourceZ : new float[]{0, -10}) {
                        glUniform3f(glGetUniformLocation(program, "_gr_source"), 0, 0, sourceZ);
                        glUniform3f(glGetUniformLocation(program, "_gr_amount"), active ? 0.7f : 0, active ? 1 : 0, active ? 1 : 0);
                        glClearColor(0.25f, 0.4f, 0.6f, 0.7f);
                        glClear(GL_COLOR_BUFFER_BIT);
                        glUniform1i(glGetUniformLocation(program, "_gr_add"), 0);
                        glBlendFuncSeparate(GL_DST_COLOR, GL_ZERO, GL_ZERO, GL_ONE);
                        glDrawArrays(GL_TRIANGLES, 0, 3);
                        glUniform1i(glGetUniformLocation(program, "_gr_add"), 1);
                        glBlendFuncSeparate(GL_ONE, GL_ONE, GL_ZERO, GL_ONE);
                        glDrawArrays(GL_TRIANGLES, 0, 3);
                        float[] pixels = new float[32 * 32 * 4];
                        glReadPixels(0, 0, 32, 32, GL_RGBA, GL_FLOAT, pixels);
                        for (int i = 0; i < pixels.length; i++) {
                            assertThat(pixels[i]).isFinite();
                            if (i % 4 == 3) assertThat(pixels[i]).isEqualTo(0.7f);
                            else if (!active) assertThat(pixels[i]).isCloseTo(new float[]{0.25f, 0.4f, 0.6f}[i % 4], within(0.00001f));
                        }
                        if (active) assertThat(pixels[0]).isGreaterThan(0.25f);
                    }
                }
                assertThat(glGetError()).isEqualTo(GL_NO_ERROR);
            } finally {
                glDeleteProgram(program);
                glDeleteVertexArrays(vao);
                glDeleteTextures(texture);
                glDeleteFramebuffers(fbo);
            }
        }
    }
}
