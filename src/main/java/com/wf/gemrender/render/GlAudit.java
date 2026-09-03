package com.wf.gemrender.render;

import static org.lwjgl.opengl.GL33C.GL_ACTIVE_TEXTURE;
import static org.lwjgl.opengl.GL33C.GL_ARRAY_BUFFER_BINDING;
import static org.lwjgl.opengl.GL33C.GL_BLEND;
import static org.lwjgl.opengl.GL33C.GL_BLEND_DST_ALPHA;
import static org.lwjgl.opengl.GL33C.GL_BLEND_DST_RGB;
import static org.lwjgl.opengl.GL33C.GL_BLEND_EQUATION_ALPHA;
import static org.lwjgl.opengl.GL33C.GL_BLEND_EQUATION_RGB;
import static org.lwjgl.opengl.GL33C.GL_BLEND_SRC_ALPHA;
import static org.lwjgl.opengl.GL33C.GL_BLEND_SRC_RGB;
import static org.lwjgl.opengl.GL33C.GL_COLOR_WRITEMASK;
import static org.lwjgl.opengl.GL33C.GL_CULL_FACE;
import static org.lwjgl.opengl.GL33C.GL_CULL_FACE_MODE;
import static org.lwjgl.opengl.GL33C.GL_CURRENT_PROGRAM;
import static org.lwjgl.opengl.GL33C.GL_DEPTH_FUNC;
import static org.lwjgl.opengl.GL33C.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL33C.GL_DEPTH_WRITEMASK;
import static org.lwjgl.opengl.GL33C.GL_DRAW_FRAMEBUFFER_BINDING;
import static org.lwjgl.opengl.GL33C.GL_ELEMENT_ARRAY_BUFFER_BINDING;
import static org.lwjgl.opengl.GL33C.GL_NO_ERROR;
import static org.lwjgl.opengl.GL33C.GL_READ_FRAMEBUFFER_BINDING;
import static org.lwjgl.opengl.GL33C.GL_SAMPLER_BINDING;
import static org.lwjgl.opengl.GL33C.GL_SCISSOR_TEST;
import static org.lwjgl.opengl.GL33C.GL_TEXTURE0;
import static org.lwjgl.opengl.GL33C.GL_TEXTURE_BINDING_2D;
import static org.lwjgl.opengl.GL33C.GL_VERTEX_ARRAY_BINDING;
import static org.lwjgl.opengl.GL33C.glActiveTexture;
import static org.lwjgl.opengl.GL33C.glGetBooleanv;
import static org.lwjgl.opengl.GL33C.glGetError;
import static org.lwjgl.opengl.GL33C.glGetInteger;

import java.nio.ByteBuffer;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.lwjgl.system.MemoryUtil;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;

import com.wf.gemrender.GemRender;

/**
 * Names the GL state GemRender is able to change, and reports anything it changed and did not put back.
 *
 * <p>Leaked GL state is the worst class of bug this renderer can produce, because the symptom appears
 * somewhere else: a depth function left on {@code GL_ALWAYS} shows up as another mod's geometry drawing
 * through walls, a vertex array left bound shows up as the wrong mesh, and neither points at the code
 * that did it. There is a second, quieter version of the same problem — Minecraft's
 * {@link GlStateManager} keeps a shadow copy of some of this state and <em>skips</em> the GL call when
 * it believes nothing changed, so one raw GL call behind its back makes a later, necessary call not
 * happen. Both are checked here.
 *
 * <p>Off unless {@code -Dgemrender.glaudit=true}, because every field costs a {@code glGet} and a
 * {@code glGet} is a pipeline stall. Turning it on is the first thing to do about a rendering bug that
 * makes no sense.
 *
 * <pre>{@code
 * GlAudit.Scope scope = GlAudit.open("water:composite");
 * try {
 *     ...
 * } finally {
 *     scope.close();
 * }
 * }</pre>
 *
 * <p>A scope that is <em>meant</em> to leave something changed says so, and that field is then reported
 * only when it fails to change:
 *
 * <pre>{@code
 * GlAudit.Scope scope = GlAudit.open("bones:bind").changes(GlAudit.ACTIVE_TEXTURE);
 * }</pre>
 */
public final class GlAudit {
	private static final boolean ENABLED = Boolean.getBoolean("gemrender.glaudit");

	private static final String[] INTEGER_NAMES = { "activeTexture", "vertexArray", "arrayBuffer",
			"elementArrayBuffer", "program", "drawFramebuffer", "readFramebuffer", "sampler0",
			"blendSrcRgb", "blendDstRgb", "blendSrcAlpha", "blendDstAlpha", "blendEquationRgb",
			"blendEquationAlpha", "depthFunc", "cullFaceMode" };

	private static final int[] INTEGER_FIELDS = { GL_ACTIVE_TEXTURE, GL_VERTEX_ARRAY_BINDING,
			GL_ARRAY_BUFFER_BINDING, GL_ELEMENT_ARRAY_BUFFER_BINDING, GL_CURRENT_PROGRAM,
			GL_DRAW_FRAMEBUFFER_BINDING, GL_READ_FRAMEBUFFER_BINDING, GL_SAMPLER_BINDING,
			GL_BLEND_SRC_RGB, GL_BLEND_DST_RGB, GL_BLEND_SRC_ALPHA, GL_BLEND_DST_ALPHA,
			GL_BLEND_EQUATION_RGB, GL_BLEND_EQUATION_ALPHA, GL_DEPTH_FUNC, GL_CULL_FACE_MODE };

	private static final String[] BOOLEAN_NAMES = { "blend", "depthTest", "cullFace", "scissorTest",
			"depthMask" };

	private static final int[] BOOLEAN_FIELDS = { GL_BLEND, GL_DEPTH_TEST, GL_CULL_FACE, GL_SCISSOR_TEST,
			GL_DEPTH_WRITEMASK };

	/** The units a leak actually breaks: Minecraft's block atlas, lightmap and overlay. */
	private static final int[] SAMPLED_UNITS = { 0, 1, 2 };

	/** Field indices, for {@link Scope#changes(int...)}. Positions in {@link #INTEGER_FIELDS}. */
	public static final int ACTIVE_TEXTURE = 0;
	public static final int VERTEX_ARRAY = 1;
	public static final int ARRAY_BUFFER = 2;
	public static final int ELEMENT_ARRAY_BUFFER = 3;
	public static final int PROGRAM = 4;
	public static final int DRAW_FRAMEBUFFER = 5;
	public static final int READ_FRAMEBUFFER = 6;

	private static final int COLOR_MASK = 16 + 5;
	private static final int FIRST_UNIT = COLOR_MASK + 1;
	private static final int FIELD_COUNT = FIRST_UNIT + 3;

	private static final String[] NAMES = names();

	private static final Scope DISABLED = new Scope();

	/** One line per distinct message, so a leak that happens every frame is reported once. */
	private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

	private static final AtomicInteger LEAKS = new AtomicInteger();
	private static final AtomicInteger ERRORS = new AtomicInteger();

	/** Render thread only, and only while the audit is on. */
	private static ByteBuffer booleans;

	private GlAudit() {
	}

	public static boolean enabled() {
		return ENABLED;
	}

	/**
	 * Snapshots the state and returns the scope to {@link Scope#close()} when the work is done. Returns a
	 * shared do-nothing scope when the audit is off, so a call site costs one predictable branch.
	 */
	public static Scope open(String name) {
		if (!ENABLED || !RenderSystem.isOnRenderThread()) {
			return DISABLED;
		}
		Scope scope = new Scope(name);
		capture(scope.before);
		return scope;
	}

	/** Leaks seen and GL errors drained, for a run's verdict line. */
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

		/** Declares fields this scope is supposed to change, so only a <em>missing</em> change is news. */
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

			int shadow = GlStateManager._getActiveTexture() - GL_TEXTURE0;
			int real = after[ACTIVE_TEXTURE] - GL_TEXTURE0;
			if (shadow != real) {
				warn(name + " left GlStateManager believing unit " + shadow + " is active while GL is on "
						+ "unit " + real + "; the next _bindTexture binds to the wrong unit and records it "
						+ "under the wrong one");
			}
		}
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

		// Reading a unit's binding means making it active, so put back the one the snapshot recorded.
		int active = out[ACTIVE_TEXTURE];
		for (int unit : SAMPLED_UNITS) {
			glActiveTexture(GL_TEXTURE0 + unit);
			out[index++] = glGetInteger(GL_TEXTURE_BINDING_2D);
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
		}
		return names;
	}
}
