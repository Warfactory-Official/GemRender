package com.wf.gemrender.light;

import com.mojang.blaze3d.platform.GlStateManager;
//? if >=26.1 {
/*import com.wf.gemrender.render.GlPrograms;
import com.wf.gemrender.render.GlState;
import com.wf.gemrender.render.PassState;
import org.joml.Matrix4fc;
*///?} else {
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
//? if >=1.21 {
import com.mojang.blaze3d.vertex.MeshData;
//?}
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
//?}
import net.minecraft.client.Minecraft;

import java.util.Arrays;

//? if >=26.1 {
/*import static org.lwjgl.opengl.GL33C.*;
*///?}

/**
 * Glow disc per visible point light past {@link #NEAR}: additive, depth-tested, no depth write, after the level.
 * Disc flux ~ irradiance at the eye ({@code lum / (d^2 + 1)}): radius {@code K * lum^0.25} world, floor
 * {@link #MIN_PX}; alpha = {@code FLUX * E / area_px} => a far blast stays a bright dot instead of fading out.
 */
public final class LightGlare {
    static final float NEAR = 16.0f;
    private static final float FADE = 32.0f;
    private static final float K = 0.5f;
    private static final float MIN_PX = 1.5f;
    private static final float FLUX = Float.parseFloat(System.getProperty("gemrender.glareFlux", "6000"));
    private static final int SEGMENTS = 12;
    //? if >=26.1 {
    /*private static final PassState STATE = new PassState();
    private static final float[] MATRIX = new float[16];
    private static float[] vertices = new float[16 * SEGMENTS * 3 * 7];
    private static int program;
    private static int modelViewLoc;
    private static int projectionLoc;
    private static int vao;
    private static int vbo;
    *///?}

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
//? if >=26.1 {
    /*public static void draw(Matrix4fc modelView, Matrix4fc projection) {
*///?} else {
    public static void draw(Matrix4f modelView, Matrix4f projection) {
//?}
        lastDrawn = 0;
        if (count == 0) {
            return;
        }
        float rx = modelView.m00(), ry = modelView.m10(), rz = modelView.m20();
        float ux = modelView.m01(), uy = modelView.m11(), uz = modelView.m21();
        float focalPx = projection.m11() * Minecraft.getInstance().getWindow().getHeight() * 0.5f;

//? if >=26.1 {
        /*int required = count * SEGMENTS * 3 * 7;
        if (vertices.length < required) {
            vertices = new float[required];
        }
        int cursor = 0;
*///?} else {
        //? if >=1.21 {
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES,
                DefaultVertexFormat.POSITION_COLOR);
        //?} else {
        /*BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        *///?}
//?}
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
//? if >=26.1 {
                /*cursor = vertex(cursor, x, y, z, r, g, b, a);
                cursor = vertex(cursor, x + rx * c0 + ux * s0, y + ry * c0 + uy * s0, z + rz * c0 + uz * s0, r, g, b, 0);
                cursor = vertex(cursor, x + rx * c1 + ux * s1, y + ry * c1 + uy * s1, z + rz * c1 + uz * s1, r, g, b, 0);
*///?} else {
                vertex(buffer, x, y, z, r, g, b, a);
                vertex(buffer, x + rx * c0 + ux * s0, y + ry * c0 + uy * s0, z + rz * c0 + uz * s0, r, g, b, 0);
                vertex(buffer, x + rx * c1 + ux * s1, y + ry * c1 + uy * s1, z + rz * c1 + uz * s1, r, g, b, 0);
//?}
            }
            drawn++;
        }
//? if >=26.1 {
        /*if (drawn == 0) {
            return;
        }
        lastDrawn = drawn;
        STATE.saveDeep();
        try {
            ensureProgram();
            GlStateManager._glUseProgram(program);
            glUniformMatrix4fv(modelViewLoc, false, modelView.get(MATRIX));
            glUniformMatrix4fv(projectionLoc, false, projection.get(MATRIX));
            GlState.enableBlend();
            GlStateManager._blendFuncSeparate(GL_SRC_ALPHA, GL_ONE, GL_SRC_ALPHA, GL_ONE);
            GlState.blendEquation(GL_FUNC_ADD);
            GlStateManager._enableDepthTest();
            GlStateManager._depthMask(false);
            GlStateManager._disableCull();
            GlStateManager._glBindVertexArray(vao);
            GlStateManager._glBindBuffer(GL_ARRAY_BUFFER, vbo);
            glBufferData(GL_ARRAY_BUFFER, vertices, GL_STREAM_DRAW);
            glDrawArrays(GL_TRIANGLES, 0, cursor / 7);
        } finally {
            STATE.restore();
        }
*///?} else {
        //? if >=1.21 {
        MeshData mesh = buffer.build();
        if (mesh == null) {
            return;
        }
        RenderSystem.getModelViewStack().pushMatrix();
        RenderSystem.getModelViewStack().set(modelView);
        //?} else {
        /*BufferBuilder.RenderedBuffer mesh = buffer.end();
        if (drawn == 0) {
            mesh.release();
            return;
        }
        RenderSystem.getModelViewStack().pushPose();
        RenderSystem.getModelViewStack().last().pose().set(modelView);
        *///?}
        lastDrawn = drawn;
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
        //? if >=1.21 {
        RenderSystem.getModelViewStack().popMatrix();
        //?} else {
        /*RenderSystem.getModelViewStack().popPose();
        *///?}
        RenderSystem.applyModelViewMatrix();
//?}
    }

    //? if >=1.21 <26.1 {
    private static void vertex(BufferBuilder buffer, float x, float y, float z, int r, int g, int b, int a) {
        buffer.addVertex(x, y, z).setColor(r, g, b, a);
    }
    //?} elif <1.21 {
    /*private static void vertex(BufferBuilder buffer, float x, float y, float z, int r, int g, int b, int a) {
        buffer.vertex(x, y, z).color(r, g, b, a).endVertex();
    }
    *///?}

    //? if >=26.1 {
    /*private static int vertex(int offset, float x, float y, float z, int r, int g, int b, int a) {
        vertices[offset++] = x;
        vertices[offset++] = y;
        vertices[offset++] = z;
        vertices[offset++] = r / 255.0f;
        vertices[offset++] = g / 255.0f;
        vertices[offset++] = b / 255.0f;
        vertices[offset++] = a / 255.0f;
        return offset;
    }

    private static void ensureProgram() {
        if (program != 0) {
            return;
        }
        program = GlPrograms.link("light_glare", """
                #version 330 core
                layout(location = 0) in vec3 Position;
                layout(location = 1) in vec4 Color;
                uniform mat4 ModelViewMat;
                uniform mat4 ProjMat;
                out vec4 vertexColor;
                void main() {
                    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
                    vertexColor = Color;
                }
                """, """
                #version 330 core
                in vec4 vertexColor;
                out vec4 fragColor;
                void main() {
                    fragColor = vertexColor;
                }
                """);
        modelViewLoc = glGetUniformLocation(program, "ModelViewMat");
        projectionLoc = glGetUniformLocation(program, "ProjMat");
        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        GlStateManager._glBindVertexArray(vao);
        GlStateManager._glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 7 * Float.BYTES, 0L);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 4, GL_FLOAT, false, 7 * Float.BYTES, 3L * Float.BYTES);
    }
    *///?}
}
