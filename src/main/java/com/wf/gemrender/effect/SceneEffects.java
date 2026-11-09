package com.wf.gemrender.effect;

import com.mojang.blaze3d.platform.GlStateManager;
import com.wf.gemrender.GemRender;
import com.wf.gemrender.render.GlPrograms;
import com.wf.gemrender.render.GlState;
import com.wf.gemrender.render.PassState;
import com.wf.gemrender.render.Vanilla;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.io.IOException;

import static org.lwjgl.opengl.GL33C.*;

/** Frame-scoped grading, directional flash, rays and camera-sensor interference, before the hand/HUD. */
public final class SceneEffects {
    private static final PassState STATE = new PassState();
    private static float strength, flash, sensor, smoothed, haloRadius, haloStrength;
    private static int tint = 0xFFFFFF;
    private static double x, y, z;
    private static int program, vao;
    private static boolean failed;

    private SceneEffects() {}

    public static void submit(double wx, double wy, double wz, int rgb, float grade, float whiteout) {
        submit(wx, wy, wz, rgb, grade, whiteout, 0, 0);
    }

    public static void submit(double wx, double wy, double wz, int rgb, float grade, float whiteout,
                              float radius, float halo) {
        if (!Float.isFinite(radius) || !Float.isFinite(halo) || radius < 0 || halo < 0) {
            throw new IllegalArgumentException("Glow parameters must be finite and nonnegative");
        }
        if (!Double.isFinite(wx) || !Double.isFinite(wy) || !Double.isFinite(wz)
                || !Float.isFinite(grade) || !Float.isFinite(whiteout)) {
            throw new IllegalArgumentException("Scene effect parameters must be finite");
        }
        //? if >=26.1 {
        /*Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().position();
        *///?} else {
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        //?}
        float attenuation = (float) (1 / (1 + camera.distanceTo(new Vec3(wx, wy, wz)) / 500));
        float k = Math.max(0, Math.min(grade * attenuation, 0.8f));
        if (k >= strength) {
            strength = k;
            tint = rgb;
            x = wx; y = wy; z = wz;
            haloRadius = radius; haloStrength = halo;
        }
        flash = Math.max(flash, Math.max(0, whiteout * attenuation));
    }

    public static void interference(float value) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Sensor strength must be finite");
        sensor = Math.max(sensor, Math.max(0, Math.min(value, 1)));
    }

    public static void clear() { strength = flash = sensor = smoothed = haloRadius = haloStrength = 0; }

    public static void draw(Matrix4fc projection, Matrix4fc view, Vec3 camera) {
        smoothed += (strength - smoothed) * 0.15f;
        float grade = smoothed, whiteout = flash, noise = sensor;
        float radius = haloRadius, halo = haloStrength;
        strength = flash = sensor = haloRadius = haloStrength = 0;
        if (failed || (grade < 0.001f && whiteout < 0.001f && noise < 0.001f)) return;
        STATE.saveDeep();
        try {
            if (program == 0) {
                program = GlPrograms.link("scene_effects", "#version 420 core\n"
                                + GlPrograms.resource(GemRender.MOD_ID, "shaders/water_split.vert"),
                        "#version 420 core\n" + GlPrograms.resource(GemRender.MOD_ID, "shaders/scene_effects.frag"));
                vao = glGenVertexArrays();
            }
            var main = Vanilla.mainTarget();
            Vanilla.bindWrite(main);
            GlStateManager._viewport(0, 0, main.width, main.height);
            GlStateManager._disableDepthTest();
            GlStateManager._depthMask(false);
            GlStateManager._disableCull();
            GlState.colorMask(true, true, true, true);
            GlState.enableBlend();
            GlStateManager._glUseProgram(program);
            Matrix4f inverse = new Matrix4f(projection).mul(view).invert();
            glUniformMatrix4fv(glGetUniformLocation(program, "_gr_inverse"), false, inverse.get(new float[16]));
            glUniform2f(glGetUniformLocation(program, "_gr_size"), main.width, main.height);
            glUniform3f(glGetUniformLocation(program, "_gr_source"), (float) (x - camera.x), (float) (y - camera.y), (float) (z - camera.z));
            glUniform3f(glGetUniformLocation(program, "_gr_tint"), ((tint >> 16) & 255) / 255f, ((tint >> 8) & 255) / 255f, (tint & 255) / 255f);
            glUniform3f(glGetUniformLocation(program, "_gr_amount"), grade, whiteout, noise);
            float distance = (float) camera.distanceTo(new Vec3(x, y, z));
            glUniform2f(glGetUniformLocation(program, "_gr_halo"), radius / Math.max(distance, 0.01f), halo);
            glUniform1f(glGetUniformLocation(program, "_gr_time"), (System.nanoTime() % 3_600_000_000_000L) / 1e9f);
            GlStateManager._glBindVertexArray(vao);
            GlState.blendEquation(GL_FUNC_ADD);
            glUniform1i(glGetUniformLocation(program, "_gr_add"), 0);
            GlStateManager._blendFuncSeparate(GL_DST_COLOR, GL_ZERO, GL_ZERO, GL_ONE);
            glDrawArrays(GL_TRIANGLES, 0, 3);
            glUniform1i(glGetUniformLocation(program, "_gr_add"), 1);
            GlStateManager._blendFuncSeparate(GL_ONE, GL_ONE, GL_ZERO, GL_ONE);
            glDrawArrays(GL_TRIANGLES, 0, 3);
        } catch (RuntimeException | IOException error) {
            failed = true;
            GemRender.LOGGER.error("Scene effects disabled: shader failed to build", error);
        } finally {
            STATE.restore();
        }
    }
}
