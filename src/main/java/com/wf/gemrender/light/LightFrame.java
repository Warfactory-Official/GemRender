package com.wf.gemrender.light;

import com.mojang.blaze3d.systems.RenderSystem;
import com.wf.gemrender.water.GpuStampTimer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector3fc;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static org.lwjgl.opengl.GL15C.GL_STREAM_DRAW;
import static org.lwjgl.opengl.GL15C.glBufferData;
import static org.lwjgl.opengl.GL15C.glGenBuffers;
import static org.lwjgl.opengl.GL11C.glGetInteger;
import static org.lwjgl.opengl.GL15C.glBindBuffer;
import static org.lwjgl.opengl.GL31C.GL_UNIFORM_BUFFER;
import static org.lwjgl.opengl.GL31C.GL_UNIFORM_BUFFER_BINDING;
import static org.lwjgl.opengl.GL30C.glBindBufferBase;

/**
 * Render thread. {@link #begin} at level render start: collect -> frustum cull -> nearest {@link Lights#MAX}
 * (shadowed: in the occupancy window => free tile or dropped; outside => unshadowed) -> UBO + section grid ->
 * occupancy -> shadow trace. {@link #end} after the hand, before the GUI: zero-light UBO.
 */
public final class LightFrame {
    public static final int UBO_BINDING = Integer.getInteger("gemrender.lightubo", 12);

    /** Header (camera frac + IrView, section + count, night sky + far count, far indices) + 5 vec4 per light. */
    static final int HEADER_BYTES = 80;
    /** Brightest lights reaching past vanilla terrain; LOD terrain loops these only. <= 8 (UBO slots). */
    static final int FAR_MAX = Math.min(8, Integer.getInteger("gemrender.lightFarMax", 8));
    static final int UBO_BYTES = HEADER_BYTES + Lights.MAX * 80;

    private static final LightFrame INSTANCE = new LightFrame();

    private final List<Light> pool = new ArrayList<>();
    private Light[] visible = new Light[Lights.MAX];
    private final LightGrid grid = new LightGrid();
    private final LightOccupancy occupancy = new LightOccupancy();
    private final LightShadows shadows = new LightShadows();
    private final FrustumIntersection frustum = new FrustumIntersection();
    private final Matrix4f viewProjection = new Matrix4f();
    private final ByteBuffer ubo = MemoryUtil.memCalloc(UBO_BYTES);
    private final Sink sink = new Sink();
    private final GpuStampTimer levelGpu = new GpuStampTimer();

    private int collected;
    private Lights.IrView irView = Lights.IrView.OFF;
    private int fullBuffer;
    private int emptyBuffer;
    private boolean active;

    private int lastCollected;
    private int lastVisible;
    private int lastShaded;
    private int lastShadowed;
    private int lastShadowDropped;
    private int lastFar;
    private long cpuNanos;
    private long cpuSamples;
    private long frames;

    private LightFrame() {
    }

    public static LightFrame getInstance() {
        return INSTANCE;
    }

    /** First program link or first frame, whichever is earlier. */
    void ensureBuffers() {
        if (fullBuffer != 0) {
            return;
        }
        int previous = glGetInteger(GL_UNIFORM_BUFFER_BINDING);
        fullBuffer = glGenBuffers();
        emptyBuffer = glGenBuffers();
        ByteBuffer zeros = MemoryUtil.memCalloc(UBO_BYTES);
        glBindBuffer(GL_UNIFORM_BUFFER, emptyBuffer);
        glBufferData(GL_UNIFORM_BUFFER, zeros, GL_STREAM_DRAW);
        glBindBuffer(GL_UNIFORM_BUFFER, fullBuffer);
        glBufferData(GL_UNIFORM_BUFFER, zeros, GL_STREAM_DRAW);
        MemoryUtil.memFree(zeros);
        glBindBufferBase(GL_UNIFORM_BUFFER, UBO_BINDING, emptyBuffer);
        glBindBuffer(GL_UNIFORM_BUFFER, previous);
    }

    //? if >=26.1 {
    /*public void begin(Vec3 eye, Matrix4f view, Matrix4f projection, float partialTick) {
    *///?} else {
    public void begin(Camera camera, Matrix4f view, Matrix4f projection, float partialTick) {
    //?}
        RenderSystem.assertOnRenderThread();
        frames++;
        levelGpu.begin();
        LightGlare.clear();
        if (LightShaders.standDown()) {
            return;
        }
        long start = System.nanoTime();
        ensureBuffers();

        collected = 0;
        irView = Lights.irView();
        for (Lights.Provider provider : Lights.providers()) {
            provider.collect(sink, partialTick);
        }

        //? if <26.1 {
        Vec3 eye = camera.getPosition();
        //?}
        frustum.set(viewProjection.set(projection).mul(view));
        if (visible.length < collected) {
            visible = new Light[pool.size()];
        }
        int count = 0;
        for (int i = 0; i < collected; i++) {
            Light light = pool.get(i);
            light.relativeTo(eye);
            if (frustum.testSphere(light.boundX, light.boundY, light.boundZ, light.boundR)) {
                visible[count++] = light;
                LightGlare.add(light);
            }
        }
        Arrays.sort(visible, 0, count, NEAREST);

        int camSx = (int) Math.floor(eye.x / 16.0);
        int camSy = (int) Math.floor(eye.y / 16.0);
        int camSz = (int) Math.floor(eye.z / 16.0);
        occupancy.update(Minecraft.getInstance().level, camSx, camSy, camSz);
        shadows.begin();
        grid.begin();
        ubo.clear();
        ubo.putFloat((float) (eye.x - camSx * 16.0)).putFloat((float) (eye.y - camSy * 16.0))
                .putFloat((float) (eye.z - camSz * 16.0)).putFloat(irView.ordinal());
        ubo.putInt(camSx).putInt(camSy).putInt(camSz).putInt(0);
        float night = irView == Lights.IrView.OFF ? 0.0f : Lights.nightSky(irView);
        ubo.putFloat(night).putFloat(0.0f).putFloat(0.0f).putFloat(0.0f);
        ubo.position(HEADER_BYTES);
        float near = Minecraft.getInstance().options.getEffectiveRenderDistance() * 16.0f - 16.0f;
        int far = 0;
        int shaded = 0;
        int dropped = 0;
        for (int i = 0; i < count && shaded < Lights.MAX; i++) {
            Light light = visible[i];
            light.tile = -1;
            if (light.shadow) {
                boolean inside = occupancy.inWindow((int) Math.floor(light.x / 16.0),
                        (int) Math.floor(light.y / 16.0), (int) Math.floor(light.z / 16.0));
                light.tile = inside ? shadows.claim(shaded) : -1;
                if (inside && light.tile < 0) {
                    dropped++;
                    continue;
                }
            }
            light.write(ubo);
            if (light.distance + 2.0 * light.boundR > near) {
                far = farInsert(far, shaded, light.r + light.g + light.b);
            }
            grid.add(shaded++, light, camSx, camSy, camSz, eye.x, eye.y, eye.z);
        }
        ubo.putInt(28, shaded);
        ubo.putFloat(36, far);
        lastFar = far;
        for (int k = 0; k < far; k++) {
            ubo.putInt(48 + 4 * k, farSlot[k]);
        }
        ubo.position(UBO_BYTES).flip();

        int previous = glGetInteger(GL_UNIFORM_BUFFER_BINDING);
        glBindBuffer(GL_UNIFORM_BUFFER, fullBuffer);
        glBufferData(GL_UNIFORM_BUFFER, ubo, GL_STREAM_DRAW);
        glBindBuffer(GL_UNIFORM_BUFFER, previous);
        grid.upload();
        occupancy.upload();
        LightCookies.bind();
        glBindBufferBase(GL_UNIFORM_BUFFER, UBO_BINDING, shaded == 0 && night == 0.0f ? emptyBuffer : fullBuffer);
        shadows.trace();
        active = shaded > 0 || night > 0.0f;
        IrTarget.pass(irView == Lights.IrView.SPLIT);

        Arrays.fill(visible, 0, count, null);
        lastCollected = collected;
        lastVisible = count;
        lastShaded = shaded;
        lastShadowed = shadows.used();
        lastShadowDropped = dropped;
        cpuNanos += System.nanoTime() - start;
        cpuSamples++;
    }

    public void end() {
        IrTarget.end();
        levelGpu.end();
        if (!active) {
            return;
        }
        active = false;
        glBindBufferBase(GL_UNIFORM_BUFFER, UBO_BINDING, emptyBuffer);
    }

    public String report() {
        return "lightsCollected=" + lastCollected + " lightsVisible=" + lastVisible + " lightsShaded=" + lastShaded
                + " far=" + lastFar + " glares=" + LightGlare.lastDrawn + " shadowed=" + lastShadowed + " shadowDropped=" + lastShadowDropped
                + " cellTests=" + grid.cellTests() + " cellsLit=" + grid.cellsLit()
                + " lightCpuUs=" + (cpuSamples == 0 ? 0 : cpuNanos / cpuSamples / 1000)
                + " " + occupancy.report(cpuSamples) + " " + shadows.report()
                + " levelGpuUs=" + levelGpu.meanMicros();
    }

    LightOccupancy occupancy() {
        return occupancy;
    }

    public static void blockChanged(BlockPos pos, BlockState state) {
        INSTANCE.occupancy.blockChanged(pos, state);
    }

    public static void chunkChanged(int cx, int cz) {
        INSTANCE.occupancy.chunkChanged(cx, cz);
    }

    public long frames() {
        return frames;
    }

    public void resetRun() {
        cpuNanos = 0;
        cpuSamples = 0;
        levelGpu.reset();
        occupancy.resetRun();
        shadows.resetRun();
    }

    private final int[] farSlot = new int[FAR_MAX];
    private final float[] farScore = new float[FAR_MAX];

    /** Keeps the {@link #FAR_MAX} highest-scoring slots, descending. @return new count */
    private int farInsert(int count, int slot, float score) {
        int at = count;
        while (at > 0 && farScore[at - 1] < score) {
            at--;
        }
        if (at == FAR_MAX) {
            return count;
        }
        int moved = Math.min(count, FAR_MAX - 1) - at;
        System.arraycopy(farSlot, at, farSlot, at + 1, moved);
        System.arraycopy(farScore, at, farScore, at + 1, moved);
        farSlot[at] = slot;
        farScore[at] = score;
        return Math.min(count + 1, FAR_MAX);
    }

    private Light next() {
        if (collected == pool.size()) {
            pool.add(new Light());
        }
        return pool.get(collected++);
    }

    private static final Comparator<Light> NEAREST = Comparator.comparingDouble(light -> light.distance);

    private final class Sink implements LightSink {
        @Override
        public void point(double x, double y, double z, float range, int rgb, float intensity, boolean shadow,
                          boolean ir) {
            if (ir && irView == Lights.IrView.OFF) {
                return;
            }
            next().set(x, y, z, range, rgb, intensity, false, 0, 0, 0, 0, 0, 0, -2.0f, 1.0f, -1, shadow);
            pool.get(collected - 1).ir = ir;
        }

        @Override
        public void point(double x, double y, double z, float range, int rgb, float intensity, boolean shadow,
                          boolean ir, float softness) {
            point(x, y, z, range, rgb, intensity, shadow, ir);
            pool.get(collected - 1).softness = softness;
        }

        @Override
        public void spot(double x, double y, double z, Vector3fc direction, Vector3fc up, float range,
                         float innerDegrees, float outerDegrees, int rgb, float intensity,
                         @Nullable LightCookie cookie, boolean shadow, boolean ir) {
            spot(x, y, z, direction, up, range, innerDegrees, outerDegrees, rgb, intensity, cookie, shadow, ir, 0.0f);
        }

        @Override
        public void spot(double x, double y, double z, Vector3fc direction, Vector3fc up, float range,
                         float innerDegrees, float outerDegrees, int rgb, float intensity,
                         @Nullable LightCookie cookie, boolean shadow, boolean ir, float softness) {
            if (ir && irView == Lights.IrView.OFF) {
                return;
            }
            float cosOuter = (float) Math.cos(Math.toRadians(outerDegrees));
            float cosInner = (float) Math.cos(Math.toRadians(Math.min(innerDegrees, outerDegrees)));
            float ux = up.x(), uy = up.y(), uz = up.z();
            float along = ux * direction.x() + uy * direction.y() + uz * direction.z();
            ux -= along * direction.x();
            uy -= along * direction.y();
            uz -= along * direction.z();
            float length = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
            next().set(x, y, z, range, rgb, intensity, true, direction.x(), direction.y(), direction.z(),
                    ux / length, uy / length, uz / length, cosOuter, 1.0f / Math.max(cosInner - cosOuter, 1.0e-4f),
                    cookie == null ? -1 : cookie.layer(), shadow);
            pool.get(collected - 1).ir = ir;
            pool.get(collected - 1).softness = softness;
        }
    }

    static final class Light {
        double x, y, z;
        float range, r, g, b;
        boolean spot;
        float dx, dy, dz, ux, uy, uz, cosOuter, spotScale;
        int cookie;
        boolean shadow;
        boolean ir;
        /** Falloff softening radius, m (0 => the 1 m floor). */
        float softness;
        int tile = -1;

        float rx, ry, rz;
        float boundX, boundY, boundZ, boundR;
        double distance;

        void set(double x, double y, double z, float range, int rgb, float intensity, boolean spot, float dx,
                 float dy, float dz, float ux, float uy, float uz, float cosOuter, float spotScale, int cookie,
                 boolean shadow) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.range = range;
            this.r = (rgb >> 16 & 0xFF) / 255.0f * intensity;
            this.g = (rgb >> 8 & 0xFF) / 255.0f * intensity;
            this.b = (rgb & 0xFF) / 255.0f * intensity;
            this.spot = spot;
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
            this.ux = ux;
            this.uy = uy;
            this.uz = uz;
            this.cosOuter = cosOuter;
            this.spotScale = spotScale;
            this.cookie = cookie;
            this.shadow = shadow;
            this.ir = false;
            this.softness = 0.0f;
        }

        /** Camera-relative position; bound sphere = range sphere (point) or cone hull (spot). */
        void relativeTo(Vec3 eye) {
            rx = (float) (x - eye.x);
            ry = (float) (y - eye.y);
            rz = (float) (z - eye.z);
            if (!spot) {
                boundX = rx;
                boundY = ry;
                boundZ = rz;
                boundR = range;
            } else {
                float offset;
                if (cosOuter < (float) Math.sqrt(0.5)) {
                    offset = range * cosOuter;
                    boundR = range * (float) Math.sqrt(1.0 - cosOuter * cosOuter);
                } else {
                    offset = range / (2.0f * cosOuter);
                    boundR = offset;
                }
                boundX = rx + dx * offset;
                boundY = ry + dy * offset;
                boundZ = rz + dz * offset;
            }
            distance = Math.max(0.0, Math.sqrt(boundX * boundX + boundY * boundY + boundZ * boundZ) - boundR);
        }

        void write(ByteBuffer out) {
            out.putFloat(rx).putFloat(ry).putFloat(rz).putFloat(range);
            out.putFloat(r).putFloat(g).putFloat(b).putFloat(cookie);
            out.putFloat(dx).putFloat(dy).putFloat(dz).putFloat(spot ? cosOuter : -2.0f);
            out.putFloat(ux).putFloat(uy).putFloat(uz).putFloat(spotScale);
            out.putFloat(tile).putFloat(ir ? 1.0f : 0.0f).putFloat(softness).putFloat(0.0f);
        }
    }
}
