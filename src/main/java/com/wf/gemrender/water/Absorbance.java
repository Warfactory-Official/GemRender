package com.wf.gemrender.water;

import com.mojang.blaze3d.platform.GlStateManager;
import com.wf.gemrender.render.TextureUnits;
import com.wf.gemrender.render.Vanilla;
import dev.engine_room.flywheel.api.material.Material;
import dev.engine_room.flywheel.api.material.MaterialShaders;
import net.minecraft.client.Minecraft;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL12C.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL30C.*;
import static org.lwjgl.opengl.GL30C.GL_COLOR;
import static org.lwjgl.opengl.GL32C.glFramebufferTexture;
import static org.lwjgl.opengl.GL33C.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL33C.glTexImage2D;

public final class Absorbance {
    static final int WAVELET_SLOT = 5;
    static final int ABSORBANCE_SLOT = 6;
    static final int EMISSION_SLOT = 7;
    private static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("gemrender.absorbance"));
    private static final int[] DRAW_WAVELET = {GL_COLOR_ATTACHMENT0 + WAVELET_SLOT};
    private static final int[] DRAW_ABSORBANCE = {GL_COLOR_ATTACHMENT0 + ABSORBANCE_SLOT,
            GL_COLOR_ATTACHMENT0 + EMISSION_SLOT};
    /** Non-glow: emission buffer unbound, no bandwidth. */
    private static final int[] DRAW_SMOKE = {GL_COLOR_ATTACHMENT0 + ABSORBANCE_SLOT, GL_NONE};
    /** Full-res resubmit: wavelet draws already landed in the evaluate. */
    private static final int[] DRAW_NOTHING = {GL_NONE};
    private static final int WAVELET = 0;
    private static final int SMOKE = 1;
    private static final int GLOW = 2;
    /** gemrender:absorbance_band.glsl pass. */
    private static final int BAND_WHOLE = 0;
    private static final int BAND_REDUCED = 1;
    private static final int BAND_FULL = 2;
    /**
     * Projected sprite radius in reduced texels: full-res share 1 at <= LO, 0 at >= HI.
     * {@code -Dgemrender.absorbanceBand=lo:hi}; {@code -1:0} = reduced only.
     */
    private static final float[] BAND = band(System.getProperty("gemrender.absorbanceBand", "4:8"));

    private static final float[] ZERO = {0f, 0f, 0f, 0f};

    /** Set => wins over {@link #resolutionFactor(int)}. */
    private static final Integer FORCED_FACTOR = Integer.getInteger("gemrender.absorbanceFactor");
    private static final Absorbance INSTANCE = new Absorbance();
    private final GpuStampTimer chainTimer = new GpuStampTimer();
    private final ReducedAbsorbance reduced = new ReducedAbsorbance();
    private final ReducedAbsorbance reducedFront = new ReducedAbsorbance();
    private ReducedAbsorbance active = reduced;
    private volatile MaterialShaders[] shaders = new MaterialShaders[0];
    private volatile MaterialShaders[] glowShaders = new MaterialShaders[0];
    private boolean sawAbsorbance;
    private boolean sawOther;
    private boolean present;
    private boolean exclusive;
    private boolean inEvaluate;
    private boolean reducedFrame;
    /** Full-res resubmit of a reduced frame: draws stay on the OIT framebuffer. */
    private boolean fullPass;
    private int band;
    private int bandPass = -1;
    private final float[] bandTexel = new float[4];
    private volatile int factor = FORCED_FACTOR != null ? FORCED_FACTOR : 4;
    /** {@link #factor} latched at evaluate: draws and upsample agree. */
    private int frameFactor;
    private int route;
    private int accumulate;
    private int front;
    private int emission;
    private int frontEmission;
    private int width = -1;
    private int height = -1;
    private long framesPresent;
    private long framesExclusive;
    private long framesMixed;

    private Absorbance() {
    }

    private static float[] band(String spec) {
        String[] parts = spec.split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("gemrender.absorbanceBand " + spec + ": want lo:hi");
        }
        float lo = Float.parseFloat(parts[0].trim());
        float hi = Float.parseFloat(parts[1].trim());
        if (!(lo < hi)) {
            throw new IllegalArgumentException("gemrender.absorbanceBand " + spec + ": want lo:hi with lo < hi");
        }
        return new float[] {lo, hi};
    }

    public static Absorbance getInstance() {
        return INSTANCE;
    }

    public void register(MaterialShaders value) {
        register(value, false);
    }

    /**
     * {@code glow}: fragment shader MUST include {@code gemrender:absorbance.glsl} and, under
     * {@code _FLW_EVALUATE}, write {@code gemrender_emission} (unwritten bound draw buffer => undefined).
     */
    public synchronized void register(MaterialShaders value, boolean glow) {
        if (glow) {
            glowShaders = with(glowShaders, value);
        } else {
            shaders = with(shaders, value);
        }
    }

    private static MaterialShaders[] with(MaterialShaders[] current, MaterialShaders value) {
        for (MaterialShaders existing : current) {
            if (existing.equals(value)) {
                return current;
            }
        }
        MaterialShaders[] next = java.util.Arrays.copyOf(current, current.length + 1);
        next[current.length] = value;
        return next;
    }

    private static boolean contains(MaterialShaders[] ours, MaterialShaders theirs) {
        for (MaterialShaders candidate : ours) {
            if (candidate.equals(theirs)) {
                return true;
            }
        }
        return false;
    }

    private int routeOf(Material material) {
        MaterialShaders theirs = material.shaders();
        return contains(glowShaders, theirs) ? GLOW : contains(shaders, theirs) ? SMOKE : WAVELET;
    }

    /**
     * Exclusive frame: coefficient passes skipped. Fabulous keeps depth range: composite sorts against the
     * other Fabulous layers by its nearest depth.
     */
    public boolean skips(dev.engine_room.flywheel.backend.compile.PipelineCompiler.OitMode mode) {
        return exclusive && switch (mode) {
            case EVALUATE, OFF -> false;
            case DEPTH_RANGE -> !Minecraft.useShaderTransparency();
            default -> true;
        };
    }

    public void observe(Material material) {
        int mine = routeOf(material);

        if (mine != WAVELET) {
            sawAbsorbance = true;
        } else {
            sawOther = true;
        }

        if (!inEvaluate) {
            return;
        }

        if (mine != route) {
            if (reducedFrame && !fullPass && (route == WAVELET) != (mine == WAVELET)) {
                if (mine == WAVELET) {
                    active.leave();
                } else {
                    active.enter();
                }
            }
            route = mine;
            glDrawBuffers(mine == GLOW ? DRAW_ABSORBANCE : mine == SMOKE ? DRAW_SMOKE
                    : fullPass ? DRAW_NOTHING : DRAW_WAVELET);
        }
    }

    public void beginFrame() {
        present = ENABLED && sawAbsorbance;
        exclusive = present && !sawOther;

        sawAbsorbance = false;
        sawOther = false;
        inEvaluate = false;
        reducedFrame = false;

        if (present) {
            framesPresent++;
            if (exclusive) {
                framesExclusive++;
            } else {
                framesMixed++;
            }
        }

        ensureTextures();
        band(BAND_WHOLE);
        attach(accumulate, emission);

        chainTimer.begin();
    }

    public void beginEvaluate() {
        inEvaluate = true;
        route = WAVELET;

        frameFactor = factor;
        reducedFrame = exclusive && frameFactor > 1 && reduced.enabled() && reducedFront.enabled();
        active = reduced;
        if (reducedFrame) {
            reduced.prepare(Vanilla.depthTextureId(Minecraft.getInstance()
                    .getMainRenderTarget()), frameFactor);
            band(BAND_REDUCED);
        } else {
            clearAbsorbance();
        }
    }

    public void endEvaluate() {
        if (!inEvaluate) {
            return;
        }
        inEvaluate = false;
        suspendRoute();
        band(BAND_WHOLE);
    }

    /** Back on the OIT framebuffer's wavelet buffer; the next absorbance material re-enters its route. */
    public void suspendRoute() {
        if (route != WAVELET) {
            if (reducedFrame && !fullPass) {
                active.leave();
            }
            route = WAVELET;
            glDrawBuffers(DRAW_WAVELET);
        }
    }

    /**
     * Reduced frame: {@code submit} again into the full-res accumulators, the sprites below the band
     * ({@link #BAND}); the upsample adds them. {@code front}: between
     * {@link #beginFrontResubmit} and {@link #endFrontResubmit}.
     */
    public void resubmitFull(Runnable submit, boolean front) {
        if (!reducedFrame) {
            return;
        }
        suspendRoute();
        if (front) {
            attach(this.front, frontEmission);
        }
        clearAbsorbance();
        glDrawBuffers(DRAW_NOTHING);
        fullPass = true;
        band(BAND_FULL);
        try {
            submit.run();
        } finally {
            suspendRoute();
            fullPass = false;
            band(BAND_WHOLE);
        }
    }

    public void endFrame() {
        endEvaluate();
        chainTimer.end();
    }

    /** @param depthTexture what the resubmit tests against (scene + water / cloud surfaces) */
    public void beginFrontResubmit(int depthTexture) {
        if (!present) {
            return;
        }

        suspendRoute();
        if (reducedFrame) {
            reducedFront.prepare(depthTexture, frameFactor);
            active = reducedFront;
            band(BAND_REDUCED);
        } else {
            attach(front, frontEmission);
            clearAbsorbance();
        }
    }

    public void endFrontResubmit() {
        if (!present) {
            return;
        }

        suspendRoute();
        active = reduced;
        attach(accumulate, emission);
        band(BAND_WHOLE);
    }

    private void band(int pass) {
        if (pass == bandPass) {
            return;
        }
        bandPass = pass;
        int previousUnit = TextureUnits.activate(TextureUnits.ABSORBANCE_BAND);
        glBindTexture(GL_TEXTURE_2D, band);
        bandTexel[0] = pass;
        bandTexel[1] = frameFactor;
        bandTexel[2] = BAND[0];
        bandTexel[3] = BAND[1];
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, 1, 1, GL_RGBA, GL_FLOAT, bandTexel);
        TextureUnits.restore(previousUnit);
    }

    private static void attach(int absorbance, int glow) {
        glFramebufferTexture(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + ABSORBANCE_SLOT, absorbance, 0);
        glFramebufferTexture(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + EMISSION_SLOT, glow, 0);
    }

    private static void clearAbsorbance() {
        glDrawBuffers(DRAW_ABSORBANCE);
        glClearBufferfv(GL_COLOR, 0, ZERO);
        glClearBufferfv(GL_COLOR, 1, ZERO);
        glDrawBuffers(DRAW_WAVELET);
    }

    public boolean present() {
        return present;
    }

    public boolean exclusive() {
        return exclusive;
    }

    /**
     * Smoke / gas draw at 1/{@code factor} of the screen per axis (1 = full).
     * Ignored under {@code -Dgemrender.absorbanceFactor}.
     */
    public void resolutionFactor(int factor) {
        if (factor < 1) {
            throw new IllegalArgumentException("absorbance resolution factor " + factor + " < 1");
        }
        if (FORCED_FACTOR == null) {
            this.factor = factor;
        }
    }

    int frameFactor() {
        return frameFactor;
    }

    /** This frame's absorbance drew into {@link #reduced()}; composite upsamples. */
    public boolean reducedFrame() {
        return reducedFrame;
    }

    ReducedAbsorbance reduced() {
        return reduced;
    }

    ReducedAbsorbance reducedFront() {
        return reducedFront;
    }

    public int accumulateTexture() {
        return accumulate;
    }

    public int frontTexture() {
        return front;
    }

    public int emissionTexture() {
        return emission;
    }

    public int frontEmissionTexture() {
        return frontEmission;
    }

    private void ensureTextures() {
        if (band == 0) {
            int previousUnit = TextureUnits.activate(TextureUnits.ABSORBANCE_BAND);
            band = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, band);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA32F, 1, 1, 0, GL_RGBA, GL_FLOAT, (java.nio.ByteBuffer) null);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            TextureUnits.restore(previousUnit);
        }

        Minecraft mc = Minecraft.getInstance();
        int newWidth = mc.getMainRenderTarget().width;
        int newHeight = mc.getMainRenderTarget().height;

        if (width == newWidth && height == newHeight) {
            return;
        }
        width = newWidth;
        height = newHeight;

        if (accumulate != 0) {
            glDeleteTextures(accumulate);
            glDeleteTextures(front);
            glDeleteTextures(emission);
            glDeleteTextures(frontEmission);
        }

        int previousTexture = org.lwjgl.opengl.GL11C.glGetInteger(
                org.lwjgl.opengl.GL11C.GL_TEXTURE_BINDING_2D);
        try {
            accumulate = allocate();
            front = allocate();
            emission = allocate();
            frontEmission = allocate();
        } finally {
            GlStateManager._bindTexture(previousTexture);
        }
    }

    /**
     * R11F_G11F_B10F FORBIDDEN: blend truncates toward zero (radeonsi navi31), sum of 150 adds -32% (RGBA16F -2.3%).
     */
    private int allocate() {
        int texture = glGenTextures();
        GlStateManager._bindTexture(texture);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F, width, height, 0, GL_RGBA, GL_FLOAT,
                (java.nio.ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        return texture;
    }

    public void resetRun() {
        framesPresent = 0;
        framesExclusive = 0;
        framesMixed = 0;
        chainTimer.reset();
    }

    public String report() {
        String chain = "chainGpu=" + chainTimer.meanMicros() + "us";

        if (!ENABLED) {
            return "off(" + chain + ")";
        }
        if (framesPresent == 0) {
            return "idle(" + chain + ")";
        }
        return "active(frames=" + framesPresent + ",exclusive=" + framesExclusive + ",mixed="
                + framesMixed + "," + chain + ")";
    }
}
