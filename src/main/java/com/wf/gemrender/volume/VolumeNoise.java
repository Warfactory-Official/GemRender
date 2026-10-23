package com.wf.gemrender.volume;

import com.wf.gemrender.GemRender;
import com.wf.gemrender.render.TextureUnits;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.Random;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL12C.*;
import static org.lwjgl.opengl.GL12C.GL_RGBA8;
import static org.lwjgl.opengl.GL33C.glBindTexture;

public final class VolumeNoise {
    public static final int TEXTURE_UNIT = TextureUnits.VOLUME_NOISE;

    private static final int SIZE = 64;

    private static final int[] FREQUENCIES = {2, 4, 8, 16};

    private static final long SEED = 0x5DEECE66DL;

    private static final VolumeNoise INSTANCE = new VolumeNoise();

    private static final int MEAN_SAMPLES = 1 << 16;

    /** {@link #densityByShape} entries, shape 0..1. */
    public static final int SHAPE_STEPS = 17;

    private static volatile float[] densityByShape;

    private int textureId;

    private VolumeNoise() {
    }

    /**
     * {@code E[max(1.6 n - 0.3 - (1 - shape) 1.3, 0)]} of {@code gemrender_volumeDensity} at shape
     * {@code i / (SHAPE_STEPS - 1)}, over this texture (coarse and fine octaves at independent points: the shader
     * samples them at incommensurate scales).
     */
    public static float[] densityByShape() {
        float[] table = densityByShape;
        if (table == null) {
            ByteBuffer pixels = ByteBuffer.allocate(SIZE * SIZE * SIZE * 4);
            for (int channel = 0; channel < FREQUENCIES.length; channel++) {
                fill(pixels, channel, FREQUENCIES[channel]);
            }
            Random random = new Random(SEED);
            int texels = SIZE * SIZE * SIZE;
            double[] sum = new double[SHAPE_STEPS];
            for (int i = 0; i < MEAN_SAMPLES; i++) {
                int coarse = random.nextInt(texels) * 4;
                int fine = random.nextInt(texels) * 4;
                float n = (pixels.get(coarse) & 0xFF) / 255.0f * 0.55f
                        + (pixels.get(coarse + 1) & 0xFF) / 255.0f * 0.25f
                        + (pixels.get(fine + 2) & 0xFF) / 255.0f * 0.13f
                        + (pixels.get(fine + 3) & 0xFF) / 255.0f * 0.07f;
                for (int s = 0; s < SHAPE_STEPS; s++) {
                    float shape = s / (SHAPE_STEPS - 1.0f);
                    sum[s] += Math.max(n * 1.6f - 0.3f - (1.0f - shape) * 1.3f, 0.0f);
                }
            }
            table = new float[SHAPE_STEPS];
            for (int s = 0; s < SHAPE_STEPS; s++) {
                table[s] = (float) (sum[s] / MEAN_SAMPLES);
            }
            densityByShape = table;
        }
        return table;
    }

    public static VolumeNoise getInstance() {
        return INSTANCE;
    }

    private static void fill(ByteBuffer pixels, int channel, int frequency) {
        Random random = new Random(SEED + channel * 0x9E3779B9L);

        float[] lattice = new float[frequency * frequency * frequency];
        for (int i = 0; i < lattice.length; i++) {
            lattice[i] = random.nextFloat();
        }

        float scale = frequency / (float) SIZE;

        for (int z = 0; z < SIZE; z++) {
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    float value = sample(lattice, frequency, x * scale, y * scale, z * scale);
                    int at = ((z * SIZE + y) * SIZE + x) * 4 + channel;
                    pixels.put(at, (byte) Math.round(Math.min(1.0f, Math.max(0.0f, value)) * 255.0f));
                }
            }
        }
    }

    private static float sample(float[] lattice, int frequency, float x, float y, float z) {
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        int z0 = (int) Math.floor(z);

        float fx = smooth(x - x0);
        float fy = smooth(y - y0);
        float fz = smooth(z - z0);

        float c000 = at(lattice, frequency, x0, y0, z0);
        float c100 = at(lattice, frequency, x0 + 1, y0, z0);
        float c010 = at(lattice, frequency, x0, y0 + 1, z0);
        float c110 = at(lattice, frequency, x0 + 1, y0 + 1, z0);
        float c001 = at(lattice, frequency, x0, y0, z0 + 1);
        float c101 = at(lattice, frequency, x0 + 1, y0, z0 + 1);
        float c011 = at(lattice, frequency, x0, y0 + 1, z0 + 1);
        float c111 = at(lattice, frequency, x0 + 1, y0 + 1, z0 + 1);

        float x00 = lerp(c000, c100, fx);
        float x10 = lerp(c010, c110, fx);
        float x01 = lerp(c001, c101, fx);
        float x11 = lerp(c011, c111, fx);

        return lerp(lerp(x00, x10, fy), lerp(x01, x11, fy), fz);
    }

    private static float at(float[] lattice, int frequency, int x, int y, int z) {
        int ix = Math.floorMod(x, frequency);
        int iy = Math.floorMod(y, frequency);
        int iz = Math.floorMod(z, frequency);
        return lattice[(iz * frequency + iy) * frequency + ix];
    }

    private static float smooth(float t) {
        return t * t * (3.0f - 2.0f * t);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    public boolean isInitialized() {
        return textureId != 0;
    }

    public void bind() {
        if (textureId == 0) {
            create();
        }

        int previousUnit = TextureUnits.activate(TEXTURE_UNIT);
        try {
            glBindTexture(GL_TEXTURE_3D, textureId);
        } finally {
            TextureUnits.restore(previousUnit);
        }
    }

    public void delete() {
        if (textureId != 0) {
            glDeleteTextures(textureId);
            textureId = 0;
        }
    }

    private void create() {
        long start = System.nanoTime();
        ByteBuffer pixels = MemoryUtil.memAlloc(SIZE * SIZE * SIZE * 4);

        try {
            for (int channel = 0; channel < FREQUENCIES.length; channel++) {
                fill(pixels, channel, FREQUENCIES[channel]);
            }
            pixels.clear();

            textureId = glGenTextures();

            int previousUnit = TextureUnits.activate(TEXTURE_UNIT);
            try {
                glBindTexture(GL_TEXTURE_3D, textureId);
                glTexImage3D(GL_TEXTURE_3D, 0, GL_RGBA8, SIZE, SIZE, SIZE, 0, GL_RGBA, GL_UNSIGNED_BYTE,
                        pixels);
                glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
                glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
                glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_S, GL_REPEAT);
                glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_T, GL_REPEAT);
                glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_R, GL_REPEAT);
            } finally {
                TextureUnits.restore(previousUnit);
            }
        } finally {
            MemoryUtil.memFree(pixels);
        }

        GemRender.LOGGER.info("Volume noise {}^3 built on texture unit {} in {}ms", SIZE, TEXTURE_UNIT,
                (System.nanoTime() - start) / 1_000_000L);
    }
}
