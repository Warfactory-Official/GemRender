package com.wf.gemrender.gl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.lwjgl.opengl.GL46C.GL_FLOAT;
import static org.lwjgl.opengl.GL46C.GL_NEAREST;
import static org.lwjgl.opengl.GL46C.GL_RGBA;
import static org.lwjgl.opengl.GL46C.GL_RGBA32F;
import static org.lwjgl.opengl.GL46C.GL_SHADER_STORAGE_BARRIER_BIT;
import static org.lwjgl.opengl.GL46C.GL_SHADER_STORAGE_BUFFER;
import static org.lwjgl.opengl.GL46C.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL46C.GL_TEXTURE4;
import static org.lwjgl.opengl.GL46C.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL46C.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL46C.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL46C.glActiveTexture;
import static org.lwjgl.opengl.GL46C.glBindBuffer;
import static org.lwjgl.opengl.GL46C.glBindBufferBase;
import static org.lwjgl.opengl.GL46C.glBindTexture;
import static org.lwjgl.opengl.GL46C.glBufferData;
import static org.lwjgl.opengl.GL46C.glDeleteBuffers;
import static org.lwjgl.opengl.GL46C.glDeleteProgram;
import static org.lwjgl.opengl.GL46C.glDeleteTextures;
import static org.lwjgl.opengl.GL46C.glDispatchCompute;
import static org.lwjgl.opengl.GL46C.glGenBuffers;
import static org.lwjgl.opengl.GL46C.glGenTextures;
import static org.lwjgl.opengl.GL46C.glGetBufferSubData;
import static org.lwjgl.opengl.GL46C.glGetUniformLocation;
import static org.lwjgl.opengl.GL46C.glMemoryBarrier;
import static org.lwjgl.opengl.GL46C.glTexImage2D;
import static org.lwjgl.opengl.GL46C.glTexParameteri;
import static org.lwjgl.opengl.GL46C.glUniform1f;
import static org.lwjgl.opengl.GL46C.glUniform2f;
import static org.lwjgl.opengl.GL46C.glUniform3f;
import static org.lwjgl.opengl.GL46C.glUniformMatrix4fv;
import static org.lwjgl.opengl.GL46C.glUseProgram;

import org.joml.Matrix4f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** The shipped rope instance shader's absorbance band overlay, and its decode, under a transcribed Flywheel stage. */
@Tag("gl")
class RopeBandGlTest {
	private static final int BAND_UNIT = 4;

	private static final float RADIUS = 0.1f;

	private static final float VIEWPORT_HEIGHT = 1080.0f;

	private static final float FACTOR = 4.0f;

	private static final float LO = 4.0f;

	private static final float HI = 8.0f;

	private static final Matrix4f PROJECTION = new Matrix4f().perspective((float) Math.toRadians(70.0), 16.0f / 9.0f,
			0.05f, 1000.0f);

	/** Rope parameter t of each sampled vertex. */
	private static final float[] T = {0.0f, 0.5f, 1.0f};

	private static final String SOURCE = """
			#version 460 core
			layout(local_size_x = 1) in;

			layout(std430, binding = 0) writeonly buffer Out {
			    float result[];
			};

			uniform float flw_renderSeconds;
			uniform float uPass;

			// GemRenderRopeTypes layout.
			struct FlwInstance {
			    vec4 color;
			    ivec2 lightA;
			    ivec2 lightB;
			    vec3 a;
			    vec3 b;
			    vec4 shape;
			    vec4 sway;
			    vec4 sphere;
			};

			uniform vec3 uA;
			uniform vec3 uB;

			vec4 flw_vertexPos;
			vec3 flw_vertexNormal;
			vec4 flw_vertexColor;
			ivec2 flw_vertexOverlay;
			vec2 flw_vertexTexCoord;
			vec2 flw_vertexLight;

			""" + ShaderSources.inlined("instance/rope.vert") + """

			void main() {
			    FlwInstance i;
			    i.color = vec4(1.0);
			    i.lightA = ivec2(240);
			    i.lightB = ivec2(240);
			    i.a = uA;
			    i.b = uB;
			    i.shape = vec4(0.0, %s, 1.0, 0.0);
			    i.sway = vec4(0.0);
			    float ts[3] = float[](0.0, 0.5, 1.0);
			    for (int v = 0; v < 3; v++) {
			        flw_vertexPos = vec4(ts[v], 1.0, 0.0, 1.0);
			        flw_vertexNormal = vec3(0.0);
			        flw_vertexTexCoord = vec2(0.0);
			        flw_instanceVertex(i);
			        result[v * 3] = float(flw_vertexOverlay.x);
			        result[v * 3 + 1] = float(flw_vertexOverlay.y);
			        result[v * 3 + 2] = gemrender_bandShare(flw_vertexOverlay.y);
			    }
			}
			""".formatted(RADIUS);

	/** Camera at the origin looking down -Z: view = identity, depth = -z. */
	private static int expectedQ(float z) {
		float texels = RADIUS * PROJECTION.m11() * 0.5f * VIEWPORT_HEIGHT / (-z * FACTOR);
		float x = Math.max(0.0f, Math.min(1.0f, (texels - LO) / (HI - LO)));
		return (int) ((1.0f - x * x * (3.0f - 2.0f * x)) * 1023.0f + 0.5f);
	}

	private static float[] run(float pass, float az, float bz) {
		try (HeadlessGl gl = HeadlessGl.createOrSkip()) {
			int program = gl.computeProgram(SOURCE);
			int band = glGenTextures();
			int out = glGenBuffers();
			try {
				glActiveTexture(GL_TEXTURE4);
				glBindTexture(GL_TEXTURE_2D, band);
				glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA32F, 1, 1, 0, GL_RGBA, GL_FLOAT,
						new float[] {pass, FACTOR, LO, HI});
				glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
				glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

				glBindBuffer(GL_SHADER_STORAGE_BUFFER, out);
				glBufferData(GL_SHADER_STORAGE_BUFFER, new float[9], GL_STATIC_DRAW);
				glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, out);

				glUseProgram(program);
				HeadlessGl.samplerUnit(program, "_gemrender_absorbanceBand", BAND_UNIT);
				glUniformMatrix4fv(glGetUniformLocation(program, "flw_view"), false, new Matrix4f().get(new float[16]));
				glUniformMatrix4fv(glGetUniformLocation(program, "flw_projection"), false,
						PROJECTION.get(new float[16]));
				glUniform2f(glGetUniformLocation(program, "flw_viewportSize"), 1920.0f, VIEWPORT_HEIGHT);
				glUniform1f(glGetUniformLocation(program, "flw_renderSeconds"), 0.0f);
				glUniform3f(glGetUniformLocation(program, "uA"), 0.0f, 0.0f, az);
				glUniform3f(glGetUniformLocation(program, "uB"), 0.0f, 0.0f, bz);

				glDispatchCompute(1, 1, 1);
				glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
				float[] actual = new float[9];
				glGetBufferSubData(GL_SHADER_STORAGE_BUFFER, 0, actual);
				return actual;
			} finally {
				glDeleteBuffers(out);
				glDeleteTextures(band);
				glDeleteProgram(program);
			}
		}
	}

	@Test
	@DisplayName("outside a split frame: vanilla overlay, whole share")
	void whole() {
		float[] r = run(0.0f, -2.0f, -300.0f);
		for (int v = 0; v < T.length; v++) {
			assertThat(r[v * 3 + 1]).as("vertex %d overlay.y", v).isEqualTo(10.0f);
			assertThat(r[v * 3 + 2]).as("vertex %d share", v).isEqualTo(1.0f);
		}
	}

	@Test
	@DisplayName("rope on one side of the band: instance-constant, collapsible share")
	void even() {
		float[] far = run(2.0f, -200.0f, -260.0f);
		float[] near = run(1.0f, -1.0f, -1.5f);
		for (int v = 0; v < T.length; v++) {
			assertThat(far[v * 3 + 1]).as("far vertex %d", v).isEqualTo(1024.0f + 1023.0f);
			assertThat(far[v * 3 + 2]).as("far full-res share").isEqualTo(1.0f);
			assertThat(near[v * 3 + 1]).as("near vertex %d", v).isEqualTo(1024.0f);
			assertThat(near[v * 3 + 2]).as("near reduced share").isEqualTo(1.0f);
		}
	}

	@Test
	@DisplayName("rope across the band: per-vertex share, flagged non-collapsible; passes sum to 1")
	void spanning() {
		float az = -2.0f;
		float bz = -100.0f;
		float[] reduced = run(1.0f, az, bz);
		float[] full = run(2.0f, az, bz);
		for (int v = 0; v < T.length; v++) {
			int q = expectedQ(az + (bz - az) * T[v]);
			assertThat(full[v * 3 + 1]).as("vertex %d overlay.y", v).isEqualTo(1024.0f + 1024.0f + q);
			assertThat(reduced[v * 3 + 2] + full[v * 3 + 2]).as("vertex %d shares", v).isCloseTo(1.0f, within(1e-6f));
		}
		assertThat(full[2]).as("near end mostly reduced").isLessThan(0.5f);
		assertThat(full[8]).as("far end full res").isEqualTo(1.0f);
	}
}
