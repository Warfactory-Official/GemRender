package com.wf.gemrender.medium;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.wf.gemrender.GemRender;
import com.wf.gemrender.particle.ParticleBuffer;
import com.wf.gemrender.particle.ParticleStyle;
import com.wf.gemrender.render.GlAudit;
import com.wf.gemrender.render.GlPrograms;
import com.wf.gemrender.render.GlState;
import com.wf.gemrender.render.Vanilla;
import com.wf.gemrender.volume.Volume;
import com.wf.gemrender.volume.VolumeAtlas;
import com.wf.gemrender.volume.VolumeField;
import com.wf.gemrender.volume.VolumeNoise;
import com.wf.gemrender.volume.VolumeStyle;
import com.wf.gemrender.water.GpuStampTimer;
import com.wf.gemrender.render.PassState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.io.IOException;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL20C.*;
import static org.lwjgl.opengl.GL30C.*;
import static org.lwjgl.opengl.GL32C.glFramebufferTexture;

/**
 * Camera inside a cloud: the near part (billboards within {@code cull}) or all of it (a {@link Volume}) drawn as one
 * full-screen fog pass after the level, before the hand. Billboard fog reach {@code cull + CULL_RAMP / 2} matches
 * the cull ramp's missing optical depth.
 */
public final class CameraMedium {
    private static final String VERSION = "#version 420 core\n";

    private static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("gemrender.medium"));

    private static final CameraMedium INSTANCE = new CameraMedium();

    public static final int VOLUME_STEPS = 12;

    /** {@code GEMRENDER_VOLUME_MAX_SUN_STEPS}. */
    private static final int MAX_SUN_STEPS = 8;

    /** {@code flw_light0Direction}: vanilla {@code Lighting} diffuse light 0, world space. */
    private static final float[] LIGHT0 = normalized(0.2f, 1.0f, -0.7f);

    private final PassState state = new PassState();
    private java.util.function.BooleanSupplier hidden = () -> false;
    private final GpuStampTimer timer = new GpuStampTimer();
    private final float[] matrix = new float[16];

    private boolean active;
    private float extinction;
    private float red;
    private float green;
    private float blue;
    private float blockLight;
    private float skyLight;
    private float reach;
    private AABB box;
    private Object held;
    private Volume volume;
    private float ambient;
    private float sun;
    private float phase;
    private Runnable release;

    private boolean created;
    private boolean failed;
    private int program;
    private int vao;
    private int fbo;
    private int fboColor;
    private int clipLoc;
    private int boxMinLoc;
    private int boxMaxLoc;
    private int extinctionLoc;
    private int reachLoc;
    private int tintLoc;
    private int lightLoc;
    private int stepsLoc;
    private int byShapeLoc;
    private int centerLoc;
    private int extentLoc;
    private int edgeLoc;
    private int fieldOriginLoc;
    private int fieldScaleLoc;
    private int ambientLoc;
    private int sunLoc;
    private int phaseLoc;
    private int lightDirLoc;

    private CameraMedium() {
    }

    private static float[] normalized(float x, float y, float z) {
        float length = (float) Math.sqrt(x * x + y * y + z * z);
        return new float[]{x / length, y / length, z / length};
    }

    public static CameraMedium getInstance() {
        return INSTANCE;
    }

    /**
     * Call every frame the camera is inside a cloud of {@code style} billboards drawn with
     * {@link com.wf.gemrender.particle.ParticleModels#absorbance}; {@link #outside} every other frame. Fog colour =
     * style tint x style light, as the billboards draw it.
     *
     * @param extinction per block ({@link com.wf.gemrender.particle.ParticleOptics#extinction})
     * @param box        world bounds of the cloud: fog stops at its exit
     * @param cull       billboards of {@code style} nearer than this are replaced by fog
     */
    public void inside(int style, float extinction, AABB box, float cull) {
        if (!ENABLED) {
            return;
        }
        ParticleStyle look = ParticleBuffer.getInstance()
                .style(style);
        hold(style, () -> ParticleBuffer.getInstance()
                .setStyleCull(style, 0.0f));
        ParticleBuffer.getInstance()
                .setStyleCull(style, cull);
        fog(extinction, look.tintRed, look.tintGreen, look.tintBlue, look.lightBlock, look.lightSky, box,
                cull + ParticleBuffer.CULL_RAMP * 0.5f);
    }

    /**
     * Camera inside {@code volume} (world {@code box} = centre +- extent): hidden, drawn as the noise-free mean of
     * its march ({@link VolumeNoise#densityByShape} over its shape, {@link #VOLUME_STEPS} samples per pixel; sun
     * through the march's sun path at full shape). Call every frame, {@link #outside} otherwise.
     */
    public void inside(Volume volume, AABB box) {
        if (!ENABLED) {
            return;
        }
        hold(volume, () -> volume.hidden(false));
        volume.hidden(true);
        VolumeStyle style = volume.style();
        float density = style.density() * volume.fade();
        int sunSteps = Math.min(style.sunSteps(), MAX_SUN_STEPS);
        float sunPath = sunSteps <= 0 ? 0.0f : Math.max(volume.minExtent(), 0.5f)
                * (float) (Math.pow(1.5, sunSteps) - 1.0) / (0.5f * sunSteps);
        float sunlight = sunSteps <= 0 ? 1.0f
                : (float) Math.exp(-density * VolumeNoise.densityByShape()[VolumeNoise.SHAPE_STEPS - 1]
                        * style.sunDensity() * sunPath);
        fog(density, style.red(), style.green(), style.blue(), style.blockLight(), style.skyLight(), box,
                Float.MAX_VALUE);
        this.volume = volume;
        ambient = style.ambient();
        sun = sunlight * style.sunStrength();
        phase = style.phase();
    }

    public void outside() {
        if (release != null) {
            release.run();
            release = null;
            held = null;
        }
        active = false;
    }

    private void hold(Object key, Runnable undo) {
        if (held != key && !key.equals(held)) {
            outside();
            held = key;
            release = undo;
        }
    }

    private void fog(float extinction, float red, float green, float blue, float blockLight, float skyLight,
                     AABB box, float reach) {
        active = true;
        this.extinction = extinction;
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.blockLight = blockLight;
        this.skyLight = skyLight;
        this.box = box;
        this.reach = reach;
        volume = null;
    }

    public boolean active() {
        return active;
    }

    public void resetRun() {
        timer.reset();
    }

    public String report() {
        return (active ? "active(" : "idle(") + "fogGpu=" + timer.meanMicros() + "us)";
    }

    /** After the level, main target bound. {@code projection x modelView} = camera-relative world to clip. */
    /** {@code when} true at {@link #draw} => no fog (sensor seeing through the medium). */
    public void hideWhen(java.util.function.BooleanSupplier when) {
        hidden = when;
    }

    public void draw(Matrix4fc projection, Matrix4fc modelView, Vec3 camera) {
        if (!active || hidden.getAsBoolean() || !ensureCreated()) {
            return;
        }
        RenderTarget main = Minecraft.getInstance()
                .getMainRenderTarget();
        int color = Vanilla.colorTextureId(main);
        int lightmap = Vanilla.lightmapTextureId();

        GlAudit.Scope audit = GlAudit.open("medium:fog");
        state.saveDeep();
        timer.begin();
        try {
            if (fboColor != color) {
                GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, fbo);
                glFramebufferTexture(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, color, 0);
                fboColor = color;
            }
            GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, fbo);
            GlStateManager._viewport(0, 0, main.width, main.height);
            GlStateManager._disableDepthTest();
            GlStateManager._depthMask(false);
            GlState.colorMask(true, true, true, true);
            GlStateManager._enableBlend();
            GlStateManager._blendFuncSeparate(GL_ONE, GL_ONE_MINUS_SRC_ALPHA, GL_ZERO, GL_ONE);
            GlState.blendEquation(org.lwjgl.opengl.GL14C.GL_FUNC_ADD);

            GlStateManager._glUseProgram(program);
            new Matrix4f(projection).mul(modelView)
                    .invert()
                    .get(matrix);
            glUniformMatrix4fv(clipLoc, false, matrix);
            glUniform3f(boxMinLoc, (float) (box.minX - camera.x), (float) (box.minY - camera.y),
                    (float) (box.minZ - camera.z));
            glUniform3f(boxMaxLoc, (float) (box.maxX - camera.x), (float) (box.maxY - camera.y),
                    (float) (box.maxZ - camera.z));
            glUniform1f(extinctionLoc, extinction);
            glUniform1f(reachLoc, reach);
            glUniform3f(tintLoc, red, green, blue);
            glUniform2f(lightLoc, blockLight, skyLight);
            if (volume != null) {
                VolumeStyle style = volume.style();
                VolumeField field = volume.field();
                VolumeAtlas atlas = VolumeAtlas.getInstance();
                glUniform1i(stepsLoc, VOLUME_STEPS);
                glUniform1fv(byShapeLoc, VolumeNoise.densityByShape());
                glUniform3f(centerLoc, (float) (box.getCenter().x - camera.x), (float) (box.getCenter().y - camera.y),
                        (float) (box.getCenter().z - camera.z));
                glUniform3f(extentLoc, (float) box.getXsize() * 0.5f, (float) box.getYsize() * 0.5f,
                        (float) box.getZsize() * 0.5f);
                glUniform1f(edgeLoc, style.edge());
                glUniform3f(fieldOriginLoc, field == null ? 0.0f : atlas.originU(field.tile()),
                        field == null ? 0.0f : atlas.originV(field.tile()),
                        field == null ? 0.0f : atlas.originW(field.tile()));
                glUniform1f(fieldScaleLoc, field == null ? 0.0f : atlas.scale());
                glUniform1f(ambientLoc, ambient);
                glUniform1f(sunLoc, sun);
                glUniform1f(phaseLoc, phase);
                glUniform3f(lightDirLoc, LIGHT0[0], LIGHT0[1], LIGHT0[2]);
                atlas.bind();
            } else {
                glUniform1i(stepsLoc, 0);
            }

            GlStateManager._activeTexture(GL_TEXTURE0);
            GlStateManager._bindTexture(Vanilla.depthTextureId(main));
            int pointSampler0 = GlState.pointSampler(0);
            GlStateManager._activeTexture(GL_TEXTURE1);
            GlStateManager._bindTexture(lightmap);
            GlStateManager._glBindVertexArray(vao);
            glDrawArrays(GL_TRIANGLES, 0, 3);
            GlState.restoreSampler(0, pointSampler0);
        } finally {
            timer.end();
            state.restore();
            Vanilla.bindWrite(main);
            audit.close();
        }
    }

    private boolean ensureCreated() {
        if (created) {
            return true;
        }
        if (failed) {
            return false;
        }
        try {
            program = GlPrograms.link("medium",
                    VERSION + GlPrograms.resource(GemRender.MOD_ID, "shaders/water_split.vert"),
                    VERSION + GlPrograms.resource(GemRender.MOD_ID, "shaders/medium.frag"));
            GlStateManager._glUseProgram(program);
            glUniform1i(glGetUniformLocation(program, "_gr_depth"), 0);
            glUniform1i(glGetUniformLocation(program, "_gr_lightmap"), 1);
            glUniform1i(glGetUniformLocation(program, "_gr_field"), VolumeAtlas.TEXTURE_UNIT);
            stepsLoc = glGetUniformLocation(program, "_gr_steps");
            byShapeLoc = glGetUniformLocation(program, "_gr_byShape");
            centerLoc = glGetUniformLocation(program, "_gr_center");
            extentLoc = glGetUniformLocation(program, "_gr_extent");
            edgeLoc = glGetUniformLocation(program, "_gr_edge");
            fieldOriginLoc = glGetUniformLocation(program, "_gr_fieldOrigin");
            fieldScaleLoc = glGetUniformLocation(program, "_gr_fieldScale");
            ambientLoc = glGetUniformLocation(program, "_gr_ambient");
            sunLoc = glGetUniformLocation(program, "_gr_sun");
            phaseLoc = glGetUniformLocation(program, "_gr_phase");
            lightDirLoc = glGetUniformLocation(program, "_gr_lightDir");
            clipLoc = glGetUniformLocation(program, "_gr_clipToRelative");
            boxMinLoc = glGetUniformLocation(program, "_gr_boxMin");
            boxMaxLoc = glGetUniformLocation(program, "_gr_boxMax");
            extinctionLoc = glGetUniformLocation(program, "_gr_extinction");
            reachLoc = glGetUniformLocation(program, "_gr_reach");
            tintLoc = glGetUniformLocation(program, "_gr_tint");
            lightLoc = glGetUniformLocation(program, "_gr_light");
            GlStateManager._glUseProgram(0);
            vao = glGenVertexArrays();
            fbo = glGenFramebuffers();
            created = true;
            return true;
        } catch (RuntimeException | IOException e) {
            failed = true;
            GemRender.LOGGER.error("Camera medium fog disabled: its shader failed to build. Clouds the camera is "
                    + "inside draw their near billboards again.", e);
            return false;
        }
    }
}
