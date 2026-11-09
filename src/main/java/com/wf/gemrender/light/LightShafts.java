package com.wf.gemrender.light;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.wf.gemrender.GemRender;
import com.wf.gemrender.render.GlPrograms;
import com.wf.gemrender.render.GlState;
import com.wf.gemrender.render.PassState;
import com.wf.gemrender.render.Vanilla;
import com.wf.gemrender.volume.SceneDepth;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

import static org.lwjgl.opengl.GL33C.*;

/**
 * Light shafts for every spot light, forward: after the level, per light, one scissored fullscreen triangle whose
 * fragment shader clips the view ray against the light's cone analytically, stops it at scene depth, and marches a few
 * jittered steps summing falloff x phase x the light's traced shadow tile ({@code include/shaft.glsl}). Pixels outside a
 * cone's screen box cost nothing; no extra occlusion tracing: the tile that shades surfaces also cuts the shaft.
 *
 * <p>Switches: {@code -Dgemrender.shafts=false}; {@code gemrender.shaftMax} (lights per frame, 8),
 * {@code gemrender.shaftSteps} (march steps at most, 16), {@code gemrender.shaftDensity} (scattering per metre, 0.004),
 * {@code gemrender.shaftPhase} (0.45), {@code gemrender.shaftDust} (dust modulation depth, 0.3).
 */
public final class LightShafts {
    private static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("gemrender.shafts"));
    static final int MAX = Integer.getInteger("gemrender.shaftMax", 8);
    private static final int STEPS = Integer.getInteger("gemrender.shaftSteps", 16);
    private static final float DENSITY = Float.parseFloat(System.getProperty("gemrender.shaftDensity", "0.004"));
    private static final float PHASE = Float.parseFloat(System.getProperty("gemrender.shaftPhase", "0.45"));
    private static final float DUST = Float.parseFloat(System.getProperty("gemrender.shaftDust", "0.3"));
    private static final int RING = 16;

    /** Per candidate: UBO slot, then camera-relative apex, axis, up, range, cosine of the half angle. */
    private static final int STRIDE = 14;
    private static final float[] candidates = new float[MAX * STRIDE];
    private static int count;
    static int lastDrawn;

    private static final PassState STATE = new PassState();
    private static final Matrix4f VIEW_PROJECTION = new Matrix4f();
    private static final Vector4f CLIP = new Vector4f();
    private static final float[] MATRIX3 = new float[9];
    private static int program;
    private static int vao;
    private static boolean failed;
    private static int slotLoc, viewInverseLoc, tanHalfLoc, sizeLoc, dayFadeLoc, densityLoc, timeLoc;

    private LightShafts() {
    }

    static void clear() {
        count = 0;
    }

    /** A shaded light that is in view, in UBO order, nearest first; keeps the first {@link #MAX} spots. */
    static void add(int slot, LightFrame.Light light) {
        if (!ENABLED || !light.spot || light.ir || count == MAX || light.range <= 0.0f || light.cosOuter <= 0.0f
                || light.r + light.g + light.b < 1.0e-3f) {
            return;
        }
        int o = count++ * STRIDE;
        float[] c = candidates;
        c[o] = slot;
        c[o + 1] = light.rx;
        c[o + 2] = light.ry;
        c[o + 3] = light.rz;
        c[o + 4] = light.dx;
        c[o + 5] = light.dy;
        c[o + 6] = light.dz;
        c[o + 7] = light.ux;
        c[o + 8] = light.uy;
        c[o + 9] = light.uz;
        c[o + 10] = light.range;
        c[o + 11] = light.cosOuter;
    }

    /** Pixel box {@code x0, y0, x1, y1} of the cone, or false if it is off screen. Conservative: behind the near plane = all. */
    private static boolean box(int i, Matrix4fc modelView, Matrix4fc projection, int w, int h, int[] out) {
        float[] c = candidates;
        int o = i * STRIDE;
        float range = c[o + 10], cos = c[o + 11];
        out[0] = 0;
        out[1] = 0;
        out[2] = w;
        out[3] = h;
        if (cos < 0.1f) {
            return true;
        }
        float radius = range * (float) Math.sqrt(1.0 - cos * cos) / cos * 1.08f;
        float ax = c[o + 4], ay = c[o + 5], az = c[o + 6], ux = c[o + 7], uy = c[o + 8], uz = c[o + 9];
        float vx = ay * uz - az * uy, vy = az * ux - ax * uz, vz = ax * uy - ay * ux;
        VIEW_PROJECTION.set(projection).mul(modelView);
        float minX = 2, minY = 2, maxX = -2, maxY = -2;
        for (int k = -1; k < RING; k++) {
            float px, py, pz;
            if (k < 0) {
                px = c[o + 1];
                py = c[o + 2];
                pz = c[o + 3];
            } else {
                double phi = 2.0 * Math.PI * k / RING;
                float cu = (float) Math.cos(phi) * radius, sv = (float) Math.sin(phi) * radius;
                px = c[o + 1] + ax * range + ux * cu + vx * sv;
                py = c[o + 2] + ay * range + uy * cu + vy * sv;
                pz = c[o + 3] + az * range + uz * cu + vz * sv;
            }
            VIEW_PROJECTION.transform(px, py, pz, 1.0f, CLIP);
            if (CLIP.w < 0.05f) {
                return true;
            }
            float nx = CLIP.x / CLIP.w, ny = CLIP.y / CLIP.w;
            minX = Math.min(minX, nx);
            maxX = Math.max(maxX, nx);
            minY = Math.min(minY, ny);
            maxY = Math.max(maxY, ny);
        }
        int x0 = Math.max(0, (int) Math.floor((minX * 0.5f + 0.5f) * w) - 2);
        int x1 = Math.min(w, (int) Math.ceil((maxX * 0.5f + 0.5f) * w) + 2);
        int y0 = Math.max(0, (int) Math.floor((minY * 0.5f + 0.5f) * h) - 2);
        int y1 = Math.min(h, (int) Math.ceil((maxY * 0.5f + 0.5f) * h) + 2);
        out[0] = x0;
        out[1] = y0;
        out[2] = x1;
        out[3] = y1;
        return x1 > x0 && y1 > y0;
    }

    /** {@code modelView}: camera rotation only (positions camera-relative). Light UBO still bound: before {@link LightFrame#end}. */
    public static void draw(Matrix4fc modelView, Matrix4fc projection) {
        lastDrawn = 0;
        Minecraft mc = Minecraft.getInstance();
        if (count == 0 || failed || mc.level == null) {
            return;
        }
        RenderTarget main = Vanilla.mainTarget();
        int w = main.width, h = main.height;
        int[] boxes = new int[count * 4];
        int[] one = new int[4];
        int live = 0;
        for (int i = 0; i < count; i++) {
            if (box(i, modelView, projection, w, h, one)) {
                System.arraycopy(one, 0, boxes, live * 4, 4);
                System.arraycopy(candidates, i * STRIDE, candidates, live * STRIDE, STRIDE);
                live++;
            }
        }
        count = live;
        if (live == 0 || !ensureProgram()) {
            return;
        }

        float partial = Vanilla.partialTick();
        //? if >=26.1 {
        /*Vec3 eye = mc.gameRenderer.getMainCamera().position();
        *///?} else {
        Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
        //?}
        float rain = mc.level.getRainLevel(partial);
        //? if >=26.1 {
        /*float skyDarken = mc.level.getSkyDarken();
        *///?} else {
        float skyDarken = mc.level.getSkyDarken(partial);
        //?}
        float sky = Math.max(0.0f, (skyDarken - 0.2f) / 0.8f)
                * mc.level.getBrightness(LightLayer.SKY, BlockPos.containing(eye)) / 15.0f;
        float dayFade = 1.0f - 0.9f * sky;
        float density = DENSITY * (1.0f + 3.0f * rain);
        float seconds = (mc.level.getGameTime() + partial) / 20.0f;

        SceneDepth.getInstance().capture();

        boolean scissor = glIsEnabled(GL_SCISSOR_TEST);
        int[] previousBox = new int[4];
        glGetIntegerv(GL_SCISSOR_BOX, previousBox);
        STATE.saveDeep();
        try {
            GlStateManager._glUseProgram(program);
            new Matrix3f(modelView).transpose().get(MATRIX3);
            glUniformMatrix3fv(viewInverseLoc, false, MATRIX3);
            glUniform2f(tanHalfLoc, 1.0f / projection.m00(), 1.0f / projection.m11());
            glUniform2f(sizeLoc, w, h);
            glUniform1f(dayFadeLoc, dayFade);
            glUniform1f(densityLoc, density);
            glUniform1f(timeLoc, seconds);
            GlState.enableBlend();
            GlStateManager._blendFuncSeparate(GL_ONE, GL_ONE, GL_ONE, GL_ONE);
            GlState.blendEquation(GL_FUNC_ADD);
            GlStateManager._disableDepthTest();
            GlStateManager._depthMask(false);
            GlStateManager._disableCull();
            GlStateManager._enableScissorTest();
            GlStateManager._glBindVertexArray(vao);
            for (int i = 0; i < live; i++) {
                glUniform1i(slotLoc, (int) candidates[i * STRIDE]);
                GlStateManager._scissorBox(boxes[i * 4], boxes[i * 4 + 1], boxes[i * 4 + 2] - boxes[i * 4],
                        boxes[i * 4 + 3] - boxes[i * 4 + 1]);
                glDrawArrays(GL_TRIANGLES, 0, 3);
                lastDrawn++;
            }
        } finally {
            STATE.restore();
            if (scissor) {
                GlStateManager._enableScissorTest();
            } else {
                GlStateManager._disableScissorTest();
            }
            GlStateManager._scissorBox(previousBox[0], previousBox[1], previousBox[2], previousBox[3]);
        }
    }

    private static boolean ensureProgram() {
        if (program != 0) {
            return true;
        }
        try {
            program = GlPrograms.link("light shafts", "#version 150\n" + LightShadows.source("light_trace.vert"),
                    "#version 150\n" + LightShaders.include() + LightShadows.source("include/shaft.glsl")
                            + LightShadows.source("shaft.frag"));
        } catch (RuntimeException e) {
            failed = true;
            GemRender.LOGGER.error("Light shafts disabled: the shader failed to build.", e);
            return false;
        }
        LightShaders.bindProgram(program);
        int previous = glGetInteger(GL_CURRENT_PROGRAM);
        glUseProgram(program);
        glUniform1i(glGetUniformLocation(program, "_gs_scene"), SceneDepth.TEXTURE_UNIT);
        glUniform1i(glGetUniformLocation(program, "_gs_steps"), STEPS);
        glUniform1f(glGetUniformLocation(program, "_gs_phaseG"), PHASE);
        glUniform1f(glGetUniformLocation(program, "_gs_dust"), DUST);
        slotLoc = glGetUniformLocation(program, "_gs_slot");
        viewInverseLoc = glGetUniformLocation(program, "_gs_viewInverse");
        tanHalfLoc = glGetUniformLocation(program, "_gs_tanHalf");
        sizeLoc = glGetUniformLocation(program, "_gs_size");
        dayFadeLoc = glGetUniformLocation(program, "_gs_dayFade");
        densityLoc = glGetUniformLocation(program, "_gs_density");
        timeLoc = glGetUniformLocation(program, "_gs_time");
        glUseProgram(previous);
        vao = glGenVertexArrays();
        return true;
    }
}
