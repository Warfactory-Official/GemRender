package com.wf.gemrender.light;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

import java.util.Arrays;

/**
 * Glow disc per visible point light past {@link #NEAR}: additive, depth-tested, no depth write, after the level.
 * Disc flux ~ irradiance at the eye ({@code lum / (d^2 + 1)}): radius {@code K * lum^0.25} world, floor
 * {@link #MIN_PX}; alpha = {@code FLUX * E / area_px} => a far blast stays a bright dot instead of fading out.
 */
final class LightGlare {
    static final float NEAR = 16.0f;
    private static final float FADE = 32.0f;
    private static final float K = 0.5f;
    private static final float MIN_PX = 1.5f;
    private static final float FLUX = Float.parseFloat(System.getProperty("gemrender.glareFlux", "6000"));
    private static final int SEGMENTS = 12;

    /** Per glare: camera-relative x, y, z, lum, r, g, b (max component 1). */
    private static float[] glares = new float[7 * 16];
    private static int count;
    static int lastDrawn;

    private LightGlare() {
    }

    static void clear() {
        count = 0;
    }

    static void add(LightFrame.Light light) {
        if (light.spot || light.ir) {
            return;
        }
        float lum = light.r + light.g + light.b;
        float peak = Math.max(light.r, Math.max(light.g, light.b));
        if (peak <= 0.0f || light.rx * light.rx + light.ry * light.ry + light.rz * light.rz < NEAR * NEAR) {
            return;
        }
        if (glares.length < (count + 1) * 7) {
            glares = Arrays.copyOf(glares, glares.length * 2);
        }
        int o = count++ * 7;
        glares[o] = light.rx;
        glares[o + 1] = light.ry;
        glares[o + 2] = light.rz;
        glares[o + 3] = lum;
        glares[o + 4] = light.r / peak;
        glares[o + 5] = light.g / peak;
        glares[o + 6] = light.b / peak;
    }

    /** {@code modelView}: camera rotation only (positions camera-relative). */
    static void draw(Matrix4f modelView, Matrix4f projection) {
        lastDrawn = 0;
        if (count == 0) {
            return;
        }
        float rx = modelView.m00(), ry = modelView.m10(), rz = modelView.m20();
        float ux = modelView.m01(), uy = modelView.m11(), uz = modelView.m21();
        float focalPx = projection.m11() * Minecraft.getInstance().getWindow().getHeight() * 0.5f;

        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES,
                DefaultVertexFormat.POSITION_COLOR);
        int drawn = 0;
        for (int i = 0; i < count; i++) {
            int o = i * 7;
            float x = glares[o], y = glares[o + 1], z = glares[o + 2], lum = glares[o + 3];
            float d = (float) Math.sqrt(x * x + y * y + z * z);
            float radius = Math.max(K * (float) Math.sqrt(Math.sqrt(lum)), MIN_PX * d / focalPx);
            float radiusPx = radius * focalPx / d;
            float area = (float) Math.PI * radiusPx * radiusPx / 3.0f;
            float alpha = Math.min(1.0f, FLUX * lum / (d * d + 1.0f) / area)
                    * Math.min(1.0f, (d - NEAR) / FADE);
            if (alpha < 1.0f / 255.0f) {
                continue;
            }
            // Pull toward the camera by the radius: the disc is not cut by the surface it sits on.
            float pull = Math.max(0.0f, d - radius) / d;
            x *= pull;
            y *= pull;
            z *= pull;
            int a = (int) (alpha * 255.0f);
            int r = (int) (glares[o + 4] * 255.0f), g = (int) (glares[o + 5] * 255.0f), b = (int) (glares[o + 6] * 255.0f);
            for (int s = 0; s < SEGMENTS; s++) {
                double a0 = 2.0 * Math.PI * s / SEGMENTS;
                double a1 = 2.0 * Math.PI * (s + 1) / SEGMENTS;
                float c0 = (float) Math.cos(a0) * radius, s0 = (float) Math.sin(a0) * radius;
                float c1 = (float) Math.cos(a1) * radius, s1 = (float) Math.sin(a1) * radius;
                buffer.addVertex(x, y, z).setColor(r, g, b, a);
                buffer.addVertex(x + rx * c0 + ux * s0, y + ry * c0 + uy * s0, z + rz * c0 + uz * s0).setColor(r, g, b, 0);
                buffer.addVertex(x + rx * c1 + ux * s1, y + ry * c1 + uy * s1, z + rz * c1 + uz * s1).setColor(r, g, b, 0);
            }
            drawn++;
        }
        MeshData mesh = buffer.build();
        if (mesh == null) {
            return;
        }
        lastDrawn = drawn;
        RenderSystem.getModelViewStack().pushMatrix();
        RenderSystem.getModelViewStack().set(modelView);
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        BufferUploader.drawWithShader(mesh);
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.getModelViewStack().popMatrix();
        RenderSystem.applyModelViewMatrix();
    }
}
