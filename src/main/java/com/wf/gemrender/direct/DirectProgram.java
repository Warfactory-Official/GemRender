package com.wf.gemrender.direct;

import com.wf.gemrender.GemRender;
import com.wf.gemrender.render.BoneBuffer;
import com.wf.gemrender.render.MorphBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.lwjgl.opengl.GL20C.*;

final class DirectProgram {
    private static final String VERSION = "#version 330 core\n";

    private static final DirectProgram INSTANCE = new DirectProgram();

    private int program;

    private int modelViewLoc;
    private int projectionLoc;
    private int light0Loc;
    private int light1Loc;
    private int alphaCutoffLoc;

    private boolean created;
    private boolean failed;

    private DirectProgram() {
    }

    static DirectProgram getInstance() {
        return INSTANCE;
    }

    private static int link(String name, String vertexSource, String fragmentSource) {
        int vertex = compile(name + ".vert", GL_VERTEX_SHADER, vertexSource);
        int fragment = compile(name + ".frag", GL_FRAGMENT_SHADER, fragmentSource);

        int linked = glCreateProgram();
        glAttachShader(linked, vertex);
        glAttachShader(linked, fragment);
        glLinkProgram(linked);

        glDeleteShader(vertex);
        glDeleteShader(fragment);

        if (glGetProgrami(linked, GL_LINK_STATUS) == 0) {
            throw new IllegalStateException("Could not link " + name + ": " + glGetProgramInfoLog(linked));
        }
        return linked;
    }

    private static int compile(String name, int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);

        if (glGetShaderi(shader, GL_COMPILE_STATUS) == 0) {
            throw new IllegalStateException("Could not compile " + name + ": " + glGetShaderInfoLog(shader));
        }
        return shader;
    }

    private static String resource(String namespace, String path) throws IOException {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace, path);
        try (InputStream in = Minecraft.getInstance()
                .getResourceManager()
                .open(id)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8) + "\n";
        }
    }

    boolean ensureCreated() {
        if (created) {
            return true;
        }
        if (failed) {
            return false;
        }

        try {
            String skinning = resource(GemRender.MOD_ID, "flywheel/skin_lbs.glsl");
            String morph = resource(GemRender.MOD_ID, "flywheel/morph.glsl");

            program = link("direct",
                    VERSION + skinning + morph + resource(GemRender.MOD_ID, "shaders/direct.vert"),
                    VERSION + resource(GemRender.MOD_ID, "shaders/direct.frag"));

            modelViewLoc = glGetUniformLocation(program, "_gr_modelView");
            projectionLoc = glGetUniformLocation(program, "_gr_projection");
            light0Loc = glGetUniformLocation(program, "_gr_light0");
            light1Loc = glGetUniformLocation(program, "_gr_light1");
            alphaCutoffLoc = glGetUniformLocation(program, "_gr_alphaCutoff");

            glUseProgram(program);
            glUniform1i(glGetUniformLocation(program, "_gr_atlas"), DirectRenderer.UNIT_ATLAS);
            glUniform1i(glGetUniformLocation(program, "_gr_lightmap"), DirectRenderer.UNIT_LIGHTMAP);
            glUniform1i(glGetUniformLocation(program, "_gr_overlayTex"), DirectRenderer.UNIT_OVERLAY);
            glUniform1i(glGetUniformLocation(program, "_gemrender_bones"), BoneBuffer.direct()
                    .unit());
            glUniform1i(glGetUniformLocation(program, "_gemrender_morphs"), MorphBuffer.TEXTURE_UNIT);
            glUseProgram(0);

            created = true;
            GemRender.LOGGER.info("Direct program linked; items, armour and held models will draw "
                    + "instanced with GPU skinning");
            return true;
        } catch (RuntimeException | IOException e) {
            failed = true;
            GemRender.LOGGER.error("Direct path disabled: its shaders failed to build. GemRender models "
                    + "will not draw as items, armour or in hand.", e);
            return false;
        }
    }

    int id() {
        return program;
    }

    void use() {
        glUseProgram(program);
    }

    void matrices(Matrix4f modelView, Matrix4f projection) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            glUniformMatrix4fv(modelViewLoc, false, modelView.get(stack.mallocFloat(16)));
            glUniformMatrix4fv(projectionLoc, false, projection.get(stack.mallocFloat(16)));
        }
    }

    void lightDirections(Vector3f light0, Vector3f light1) {
        glUniform3f(light0Loc, light0.x(), light0.y(), light0.z());
        glUniform3f(light1Loc, light1.x(), light1.y(), light1.z());
    }

    void alphaCutoff(float cutoff) {
        glUniform1f(alphaCutoffLoc, cutoff);
    }

    void delete() {
        if (program != 0) {
            glDeleteProgram(program);
            program = 0;
        }
        created = false;
        failed = false;
    }
}
