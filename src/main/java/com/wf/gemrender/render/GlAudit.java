package com.wf.gemrender.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.wf.gemrender.GemRender;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.lwjgl.opengl.GL33C.*;

public final class GlAudit {
    public static final int ACTIVE_TEXTURE = 0;
    public static final int VERTEX_ARRAY = 1;
    public static final int ARRAY_BUFFER = 2;
    public static final int ELEMENT_ARRAY_BUFFER = 3;
    public static final int PROGRAM = 4;
    public static final int DRAW_FRAMEBUFFER = 5;
    public static final int READ_FRAMEBUFFER = 6;
    private static final boolean ENABLED = Boolean.getBoolean("gemrender.glaudit");
    private static final String[] INTEGER_NAMES = {"activeTexture", "vertexArray", "arrayBuffer",
            "elementArrayBuffer", "program", "drawFramebuffer", "readFramebuffer",
            "blendSrcRgb", "blendDstRgb", "blendSrcAlpha", "blendDstAlpha", "blendEquationRgb",
            "blendEquationAlpha", "depthFunc", "cullFaceMode"};
    private static final int[] INTEGER_FIELDS = {GL_ACTIVE_TEXTURE, GL_VERTEX_ARRAY_BINDING,
            GL_ARRAY_BUFFER_BINDING, GL_ELEMENT_ARRAY_BUFFER_BINDING, GL_CURRENT_PROGRAM,
            GL_DRAW_FRAMEBUFFER_BINDING, GL_READ_FRAMEBUFFER_BINDING,
            GL_BLEND_SRC_RGB, GL_BLEND_DST_RGB, GL_BLEND_SRC_ALPHA, GL_BLEND_DST_ALPHA,
            GL_BLEND_EQUATION_RGB, GL_BLEND_EQUATION_ALPHA, GL_DEPTH_FUNC, GL_CULL_FACE_MODE};
    private static final String[] BOOLEAN_NAMES = {"blend", "depthTest", "cullFace", "scissorTest",
            "depthMask"};
    private static final int[] BOOLEAN_FIELDS = {GL_BLEND, GL_DEPTH_TEST, GL_CULL_FACE, GL_SCISSOR_TEST,
            GL_DEPTH_WRITEMASK};
    private static final int[] SAMPLED_UNITS = {0, 1, 2, 3, 4};
    private static final int COLOR_MASK = INTEGER_FIELDS.length + BOOLEAN_FIELDS.length;
    private static final int FIRST_UNIT = COLOR_MASK + 1;

    private static final int FIELD_COUNT = FIRST_UNIT + SAMPLED_UNITS.length * 2;

    private static final String[] NAMES = names();

    private static final Scope DISABLED = new Scope();

    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    private static final AtomicInteger LEAKS = new AtomicInteger();
    private static final AtomicInteger ERRORS = new AtomicInteger();

    private static ByteBuffer booleans;

    private GlAudit() {
    }

    public static boolean enabled() {
        return ENABLED;
    }

    public static Scope open(String name) {
        if (!ENABLED || !RenderSystem.isOnRenderThread()) {
            return DISABLED;
        }
        Scope scope = new Scope(name);
        capture(scope.before);
        return scope;
    }

    public static String report() {
        if (!ENABLED) {
            return "off";
        }
        int leaks = LEAKS.get();
        int errors = ERRORS.get();
        return leaks == 0 && errors == 0 ? "clean" : "leaks=" + leaks + ",glErrors=" + errors;
    }

    public static void resetRun() {
        REPORTED.clear();
        LEAKS.set(0);
        ERRORS.set(0);
    }

    private static void warn(String message) {
        LEAKS.incrementAndGet();
        if (REPORTED.add(message)) {
            GemRender.LOGGER.warn("GL audit: {}", message);
        }
    }

    private static void drainErrors(String scope) {
        int error;
        while ((error = glGetError()) != GL_NO_ERROR) {
            ERRORS.incrementAndGet();
            String message = scope + " raised GL error " + hex(error);
            if (REPORTED.add(message)) {
                GemRender.LOGGER.warn("GL audit: {}", message);
            }
        }
    }

    private static void capture(int[] out) {
        int index = 0;
        for (int pname : INTEGER_FIELDS) {
            out[index++] = glGetInteger(pname);
        }
        for (int pname : BOOLEAN_FIELDS) {
            out[index++] = glGetInteger(pname);
        }

        if (booleans == null) {
            booleans = MemoryUtil.memAlloc(4);
        }
        glGetBooleanv(GL_COLOR_WRITEMASK, booleans);
        out[index++] = booleans.get(0) | booleans.get(1) << 1 | booleans.get(2) << 2
                | booleans.get(3) << 3;

        int active = out[ACTIVE_TEXTURE];
        for (int unit : SAMPLED_UNITS) {
            glActiveTexture(GL_TEXTURE0 + unit);
            out[index++] = glGetInteger(GL_TEXTURE_BINDING_2D);

            out[index++] = glGetInteger(GL_SAMPLER_BINDING);
        }
        glActiveTexture(active);
    }

    private static String hex(int value) {
        return value + " (0x" + Integer.toHexString(value) + ")";
    }

    private static String[] names() {
        String[] names = new String[FIELD_COUNT];
        int index = 0;
        for (String name : INTEGER_NAMES) {
            names[index++] = name;
        }
        for (String name : BOOLEAN_NAMES) {
            names[index++] = name;
        }
        names[index++] = "colorMask";
        for (int unit : SAMPLED_UNITS) {
            names[index++] = "texture2d[unit " + unit + "]";
            names[index++] = "sampler[unit " + unit + "]";
        }
        return names;
    }

    public static final class Scope {
        private final String name;
        private final int[] before;
        private final int[] after;
        private int expected;

        private Scope() {
            this.name = null;
            this.before = null;
            this.after = null;
        }

        private Scope(String name) {
            this.name = name;
            this.before = new int[FIELD_COUNT];
            this.after = new int[FIELD_COUNT];
        }

        public Scope changes(int... fields) {
            for (int field : fields) {
                expected |= 1 << field;
            }
            return this;
        }

        public void close() {
            if (name == null) {
                return;
            }

            drainErrors(name);
            capture(after);

            for (int field = 0; field < FIELD_COUNT; field++) {
                boolean moved = before[field] != after[field];
                boolean wanted = (expected & (1 << field)) != 0;
                if (moved == wanted) {
                    continue;
                }
                if (moved) {
                    warn(name + " left " + NAMES[field] + " at " + hex(after[field]) + ", was "
                            + hex(before[field]));
                } else {
                    warn(name + " declares that it changes " + NAMES[field] + " and did not; still "
                            + hex(after[field]));
                }
            }

            int shadow = GlState.activeTexture() - GL_TEXTURE0;
            int real = after[ACTIVE_TEXTURE] - GL_TEXTURE0;
            if (shadow != real) {
                warn(name + " left GlStateManager believing unit " + shadow + " is active while GL is on "
                        + "unit " + real + "; the next _bindTexture binds to the wrong unit and records it "
                        + "under the wrong one");
            }
        }
    }
}
