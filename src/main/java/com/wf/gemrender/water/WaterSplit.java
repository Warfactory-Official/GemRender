package com.wf.gemrender.water;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.wf.gemrender.render.GlAudit;
import com.wf.gemrender.render.GlState;
import com.wf.gemrender.render.Vanilla;
import dev.engine_room.flywheel.backend.engine.indirect.OitFramebuffer;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL12C.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL30C.*;
import static org.lwjgl.opengl.GL32C.glFramebufferTexture;
import static org.lwjgl.opengl.GL33C.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL33C.glTexImage2D;

//? if neoforge {
//?} else {
/*import net.minecraftforge.client.event.RenderLevelStageEvent;
 *///?}

public final class WaterSplit {
    private static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("gemrender.watersplit"));

    private static final boolean CLOUDS = !"false".equalsIgnoreCase(System.getProperty("gemrender.cloudsplit"));

    private static final float CLOUD_PHASE_ALL = -1.0f;

    private static final float CLOUD_PHASE_CLEAR = 0.0f;

    private static final float CLOUD_PHASE_CLOUDED = 1.0f;

    private static final WaterSplit INSTANCE = new WaterSplit();

    private final WaterSplitPrograms programs = new WaterSplitPrograms();
    private final WaterDepthPrepass prepass = new WaterDepthPrepass();

    private final PassState compositeState = new PassState();
    private final PassState frontState = new PassState();
    private final PassState cloudFrontState = new PassState();

    private final GpuPassTimer prepassTimer = new GpuPassTimer();
    private final GpuPassTimer midTimer = new GpuPassTimer();
    private final GpuPassTimer lateTimer = new GpuPassTimer();
    private final GpuPassTimer cloudTimer = new GpuPassTimer();

    private int frontTexture;
    private int frontWidth = -1;
    private int frontHeight = -1;

    private boolean prepassValid;

    private boolean armedComposite;

    private boolean pendingFront;

    private boolean pendingLateFront;

    private boolean cloudFrame;

    private boolean absorbanceFrame;

    private boolean waveletFrame;

    private boolean oitDrawsThisFrame;
    private boolean oitDrawsLastFrame;

    private int stashedAccumulate;
    private int stashedDepthBounds;
    private int stashedCoefficients;

    private long framesSplit;

    private WaterSplit() {
    }

    public static WaterSplit getInstance() {
        return INSTANCE;
    }

    private static boolean modeActive() {
        return ENABLED && !Minecraft.useShaderTransparency();
    }

    static boolean supported() {
        return WaterDepthPrepass.SUPPORTED;
    }

    private static boolean cloudsFolded() {
        return CLOUDS && WaterDepthPrepass.CLOUDS_SUPPORTED;
    }

    public void onAfterEntities(RenderLevelStageEvent event) {
        oitDrawsLastFrame = oitDrawsThisFrame;
        oitDrawsThisFrame = false;
        prepassValid = false;
        armedComposite = false;
        pendingFront = false;
        pendingLateFront = false;
        cloudFrame = false;

        if (!modeActive() || !WaterDepthPrepass.SUPPORTED || !oitDrawsLastFrame
                || !programs.ensureCreated()) {
            return;
        }

        prepassTimer.begin();
        prepass.run(event, programs, cloudsFolded());
        prepassTimer.end();
        prepassValid = true;
        cloudFrame = prepass.foldedClouds();
    }

    public void beforeOitComposite(OitFramebuffer oit, Runnable resubmit) {
        oitDrawsThisFrame = true;

        if (!prepassValid || !modeActive()) {
            return;
        }

        midTimer.begin();

        RenderTarget main = Minecraft.getInstance()
                .getMainRenderTarget();
        ensureFrontTexture(main.width, main.height);

        GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, oit.fbo);
        glFramebufferTexture(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + 5, frontTexture, 0);
        glFramebufferTexture(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, prepass.textureId(), 0);
        GlState.clearColor(0f, 0f, 0f, 0f);
        GlState.clear(GL_COLOR_BUFFER_BIT);

        Absorbance.getInstance()
                .beginFrontResubmit();

        GlAudit.Scope audit = GlAudit.open("water:oit-front");
        try {
            resubmit.run();
        } finally {
            audit.close();
            Absorbance.getInstance()
                    .endFrontResubmit();
            glFramebufferTexture(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + 5, oit.accumulate, 0);
            glFramebufferTexture(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, Vanilla.depthTextureId(main), 0);
        }

        stashedAccumulate = oit.accumulate;
        stashedDepthBounds = oit.depthBounds;
        stashedCoefficients = oit.coefficients;
        armedComposite = true;
    }

    public boolean compositeInstead(OitFramebuffer oit) {
        Absorbance absorbance = Absorbance.getInstance();

        if (!armedComposite) {
            if (!absorbance.present() || !programs.ensureCreated()) {
                return false;
            }
            compositeAbsorbance(absorbance);
            return absorbance.exclusive();
        }
        armedComposite = false;
        absorbanceFrame = absorbance.present();
        waveletFrame = !absorbance.exclusive();

        GlAudit.Scope audit = GlAudit.open("water:composite")
                .changes(GlAudit.DRAW_FRAMEBUFFER, GlAudit.READ_FRAMEBUFFER);
        compositeState.save();
        try {
            Vanilla.bindWrite(Minecraft.getInstance()
                    .getMainRenderTarget());

            GlStateManager._depthMask(false);
            GlState.colorMask(true, true, true, true);
            GlStateManager._enableBlend();
            GlStateManager._blendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE,
                    GL_ONE_MINUS_SRC_ALPHA);
            GlState.blendEquation(org.lwjgl.opengl.GL14C.GL_FUNC_ADD);
            GlStateManager._depthFunc(GL_ALWAYS);

            if (absorbanceFrame) {
                programs.drawAbsorbanceBehind(absorbance.accumulateTexture(), absorbance.frontTexture());
            }
            if (waveletFrame) {
                programs.drawBehind(oit.accumulate, frontTexture, oit.depthBounds, oit.coefficients,
                        prepass.textureId(), prepass.cloudTextureId());
            }
        } finally {
            compositeState.restore();
            Vanilla.bindWrite(Minecraft.getInstance()
                    .getMainRenderTarget());
            audit.close();
        }
        midTimer.end();

        pendingFront = true;
        framesSplit++;
        return true;
    }

    public void onAfterTranslucent(RenderLevelStageEvent event) {
        if (prepass.isRendering() || !pendingFront) {
            return;
        }
        pendingFront = false;

        pendingLateFront = waveletFrame && cloudFrame;

        lateTimer.begin();
        drawFrontHalf(frontState, "water:front", absorbanceFrame, waveletFrame,
                cloudFrame ? CLOUD_PHASE_CLEAR : CLOUD_PHASE_ALL);
        lateTimer.end();
    }

    public void onAfterWeather(RenderLevelStageEvent event) {
        if (prepass.isRendering() || !pendingLateFront) {
            return;
        }
        pendingLateFront = false;

        cloudTimer.begin();
        drawFrontHalf(cloudFrontState, "water:front-clouds", false, true, CLOUD_PHASE_CLOUDED);
        cloudTimer.end();
    }

    private void drawFrontHalf(PassState passState, String auditName, boolean absorbance, boolean wavelet,
                               float cloudPhase) {
        GlAudit.Scope audit = GlAudit.open(auditName);
        passState.save();
        try {
            Vanilla.bindWrite(Minecraft.getInstance()
                    .getMainRenderTarget());

            GlStateManager._depthMask(true);
            GlState.colorMask(true, true, true, true);
            GlStateManager._enableBlend();
            GlStateManager._blendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE,
                    GL_ONE_MINUS_SRC_ALPHA);
            GlState.blendEquation(org.lwjgl.opengl.GL14C.GL_FUNC_ADD);
            GlStateManager._enableDepthTest();
            GlStateManager._depthFunc(GL_LEQUAL);

            if (absorbance) {
                GlStateManager._depthMask(false);
                programs.drawAbsorbanceFront(Absorbance.getInstance()
                        .frontTexture());
            }
            if (wavelet) {
                GlStateManager._depthMask(true);
                programs.drawFront(stashedAccumulate, frontTexture, stashedDepthBounds, stashedCoefficients,
                        prepass.textureId(), prepass.cloudTextureId(), cloudPhase);
            }
        } finally {
            passState.restore();
            GlStateManager._activeTexture(org.lwjgl.opengl.GL13C.GL_TEXTURE0);
            audit.close();
        }
    }

    private void compositeAbsorbance(Absorbance absorbance) {

        GlAudit.Scope audit = GlAudit.open("absorbance:composite")
                .changes(GlAudit.DRAW_FRAMEBUFFER, GlAudit.READ_FRAMEBUFFER);
        compositeState.save();
        try {
            Vanilla.bindWrite(Minecraft.getInstance()
                    .getMainRenderTarget());

            GlStateManager._depthMask(false);
            GlState.colorMask(true, true, true, true);
            GlStateManager._enableBlend();
            GlStateManager._blendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE,
                    GL_ONE_MINUS_SRC_ALPHA);
            GlState.blendEquation(org.lwjgl.opengl.GL14C.GL_FUNC_ADD);
            GlStateManager._depthFunc(GL_ALWAYS);

            programs.drawAbsorbanceComposite(absorbance.accumulateTexture());
        } finally {
            compositeState.restore();
            Vanilla.bindWrite(Minecraft.getInstance()
                    .getMainRenderTarget());
            audit.close();
        }
    }

    private void ensureFrontTexture(int width, int height) {
        if (frontWidth == width && frontHeight == height) {
            return;
        }
        frontWidth = width;
        frontHeight = height;

        if (frontTexture != 0) {
            glDeleteTextures(frontTexture);
        }
        frontTexture = glGenTextures();

        int previousTexture = org.lwjgl.opengl.GL11C.glGetInteger(
                org.lwjgl.opengl.GL11C.GL_TEXTURE_BINDING_2D);
        try {
            GlStateManager._bindTexture(frontTexture);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F, width, height, 0,
                    org.lwjgl.opengl.GL11C.GL_RGBA, org.lwjgl.opengl.GL11C.GL_FLOAT,
                    (java.nio.ByteBuffer) null);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        } finally {
            GlStateManager._bindTexture(previousTexture);
        }
    }

    public void resetRun() {
        prepassTimer.reset();
        midTimer.reset();
        lateTimer.reset();
        cloudTimer.reset();
        framesSplit = 0;
    }

    public String report() {
        if (!ENABLED) {
            return "off";
        }
        if (!supported()) {
            return "unsupported";
        }
        if (framesSplit == 0) {
            return "idle";
        }
        return String.format(java.util.Locale.ROOT,
                "active(frames=%d,prepassGpu=%dus,extraOitGpu=%dus,frontGpu=%dus,clouds=%s,cpu=%dus)",
                framesSplit,
                prepassTimer.meanGpuMicros(),
                midTimer.meanGpuMicros(),
                lateTimer.meanGpuMicros(),
                cloudsFolded() ? cloudTimer.meanGpuMicros() + "us" : CLOUDS ? "unsupported" : "off",
                prepassTimer.meanCpuMicros() + midTimer.meanCpuMicros() + lateTimer.meanCpuMicros()
                        + cloudTimer.meanCpuMicros());
    }
}
