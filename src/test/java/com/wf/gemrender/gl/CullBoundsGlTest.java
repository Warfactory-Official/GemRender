package com.wf.gemrender.gl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.lwjgl.opengl.GL46C.GL_SHADER_STORAGE_BARRIER_BIT;
import static org.lwjgl.opengl.GL46C.GL_SHADER_STORAGE_BUFFER;
import static org.lwjgl.opengl.GL46C.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL46C.glBindBuffer;
import static org.lwjgl.opengl.GL46C.glBindBufferBase;
import static org.lwjgl.opengl.GL46C.glBufferData;
import static org.lwjgl.opengl.GL46C.glDeleteBuffers;
import static org.lwjgl.opengl.GL46C.glDeleteProgram;
import static org.lwjgl.opengl.GL46C.glDispatchCompute;
import static org.lwjgl.opengl.GL46C.glGenBuffers;
import static org.lwjgl.opengl.GL46C.glGetBufferSubData;
import static org.lwjgl.opengl.GL46C.glMemoryBarrier;
import static org.lwjgl.opengl.GL46C.glUniform4f;
import static org.lwjgl.opengl.GL46C.glUniformMatrix4fv;
import static org.lwjgl.opengl.GL46C.glUseProgram;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("gl")
class CullBoundsGlTest {
	private static final String CULL_SHADER = "assets/gemrender/flywheel/instance/cull/skinned.glsl";

	private static String harness() {
		String shipped = ShaderSources.read(CULL_SHADER)
				.lines()
				.filter(line -> !line.stripLeading()
						.startsWith("#include"))
				.reduce("", (a, b) -> a + b + "\n");

		return """
				#version 460 core
				layout(local_size_x = 1) in;

				layout(std430, binding = 0) writeonly buffer Out {
				    float result[];
				};

				layout(location = 0) uniform vec4 uBoneSphere;
				layout(location = 1) uniform mat4 uPose;

				// Flywheel generates this struct from the instance layout; see GemRenderInstanceTypes.SKINNED.
				struct FlwInstance {
				    vec4 color;
				    vec2 light;
				    uint boneBase;
				    vec4 boneSphere;
				    mat4 pose;
				};

				// Transcribed from flywheel:util/matrix.glsl, which the shipped file includes.
				void transformBoundingSphere(in mat4 mat, inout vec3 center, inout float radius) {
				    center = (mat * vec4(center, 1.)).xyz;
				    vec3 c0 = mat[0].xyz;
				    vec3 c1 = mat[1].xyz;
				    vec3 c2 = mat[2].xyz;
				    float scaleSqr = max(dot(c0, c0), max(dot(c1, c1), dot(c2, c2)));
				    radius *= sqrt(scaleSqr);
				}

				"""
				+ shipped
				+ """

						void main() {
						    FlwInstance i;
						    i.color = vec4(1.0);
						    i.light = vec2(0.0);
						    i.boneBase = 0u;
						    i.boneSphere = uBoneSphere;
						    i.pose = uPose;

						    // Seeded with a sphere that is nothing like the instance's, so a shader that
						    // failed to overwrite them would produce these values rather than the right ones.
						    vec3 center = vec3(-99.0, -99.0, -99.0);
						    float radius = 1234.0;

						    flw_transformBoundingSphere(i, center, radius);

						    result[0] = center.x;
						    result[1] = center.y;
						    result[2] = center.z;
						    result[3] = radius;
						}
						""";
	}

	@Test
	@DisplayName("the shipped cull shader uses the instance's sphere, not the model's")
	void theInstanceSphereReachesTheCullShader() {
		Matrix4f[] poses = {
				new Matrix4f(),
				new Matrix4f().translation(120.0f, -30.0f, 7.5f),
				new Matrix4f().translation(4.0f, 5.0f, 6.0f)
						.rotateY(0.7f)
						.rotateX(-0.3f),
				new Matrix4f().translation(-2.0f, 1.0f, 3.0f)
						.rotateZ(1.1f)
						.scale(2.5f),
		};
		float[][] spheres = {
				{ 0.0f, 0.0f, 0.0f, 1.0f },
				{ -0.781f, 1.608f, 0.0f, 2.4985f },
				{ 12.0f, -3.0f, 0.5f, 0.25f },
		};

		try (HeadlessGl gl = HeadlessGl.createOrSkip()) {
			int program = gl.computeProgram(harness());
			int outBuffer = glGenBuffers();
			try {
				glBindBuffer(GL_SHADER_STORAGE_BUFFER, outBuffer);
				glBufferData(GL_SHADER_STORAGE_BUFFER, new float[4], GL_STATIC_DRAW);
				glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, outBuffer);
				glUseProgram(program);

				float[] pose = new float[16];
				for (Matrix4f matrix : poses) {
					matrix.get(pose);
					for (float[] sphere : spheres) {
						glUniform4f(0, sphere[0], sphere[1], sphere[2], sphere[3]);
						glUniformMatrix4fv(1, false, pose);

						glDispatchCompute(1, 1, 1);
						glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);

						float[] actual = new float[4];
						glBindBuffer(GL_SHADER_STORAGE_BUFFER, outBuffer);
						glGetBufferSubData(GL_SHADER_STORAGE_BUFFER, 0, actual);

						Vector3f expectedCentre = matrix.transformPosition(
								new Vector3f(sphere[0], sphere[1], sphere[2]));
						float expectedRadius = sphere[3] * maxColumnLength(matrix);

						String where = "sphere " + java.util.Arrays.toString(sphere);
						assertThat(actual[0]).as("%s centre x", where).isEqualTo(expectedCentre.x, within(1e-3f));
						assertThat(actual[1]).as("%s centre y", where).isEqualTo(expectedCentre.y, within(1e-3f));
						assertThat(actual[2]).as("%s centre z", where).isEqualTo(expectedCentre.z, within(1e-3f));
						assertThat(actual[3]).as("%s radius", where).isEqualTo(expectedRadius, within(1e-3f));
					}
				}
			} finally {
				glDeleteBuffers(outBuffer);
				glDeleteProgram(program);
			}
		}
	}

	@Test
	@DisplayName("the shipped cull shader still discards the sphere Flywheel hands it")
	void theModelSphereIsStillDiscarded() {
		String source = ShaderSources.read(CULL_SHADER);
		assertThat(source)
				.as("cull/skinned.glsl must overwrite both halves of the incoming sphere")
				.contains("center = i.boneSphere.xyz")
				.contains("radius = i.boneSphere.w");
	}

	private static float maxColumnLength(Matrix4f m) {
		float c0 = m.m00() * m.m00() + m.m01() * m.m01() + m.m02() * m.m02();
		float c1 = m.m10() * m.m10() + m.m11() * m.m11() + m.m12() * m.m12();
		float c2 = m.m20() * m.m20() + m.m21() * m.m21() + m.m22() * m.m22();
		return (float) Math.sqrt(Math.max(c0, Math.max(c1, c2)));
	}
}
