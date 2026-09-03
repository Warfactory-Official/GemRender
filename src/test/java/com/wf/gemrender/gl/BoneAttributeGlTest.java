package com.wf.gemrender.gl;

import static org.assertj.core.api.Assertions.assertThat;
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
import static org.lwjgl.opengl.GL46C.glUseProgram;

import com.wf.gemrender.gltf.skin.BoneAttributeCodec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("gl")
class BoneAttributeGlTest {

	private static final String UNPACK_JOINTS_COMPUTE = """
			#version 460 core
			layout(local_size_x = 64) in;

			// Input as the vertex shader receives it: unsigned shorts, already divided by 256 by
			// Flywheel's vertex layout shader.
			layout(std430, binding = 0) readonly buffer Lights {
			    vec2 lights[];
			};
			layout(std430, binding = 1) writeonly buffer Joints {
			    ivec4 joints[];
			};

			"""
			+ ShaderSources.read("assets/gemrender/flywheel/skin_lbs.glsl")
			+ """

					void main() {
					    uint idx = gl_GlobalInvocationID.x;
					    if (idx >= lights.length()) {
					        return;
					    }

					    int unpacked[GEMRENDER_INFLUENCES];
					    gemrender_unpackJoints(lights[idx], unpacked);
					    joints[idx] = ivec4(unpacked[0], unpacked[1], unpacked[2], unpacked[3]);
					}
					""";

	@Test
	@DisplayName("GLSL decodes every joint index the Java encoder can produce")
	void glslAgreesWithJavaEncoder() {
		try (HeadlessGl gl = HeadlessGl.createOrSkip()) {
			int program = gl.computeProgram(UNPACK_JOINTS_COMPUTE);

			int caseCount = BoneAttributeCodec.MAX_JOINTS * BoneAttributeCodec.INFLUENCES;
			int[] expected = new int[caseCount * 4];
			float[] lightInput = new float[caseCount * 2];

			int c = 0;
			for (int joint = 0; joint < BoneAttributeCodec.MAX_JOINTS; joint++) {
				for (int influence = 0; influence < BoneAttributeCodec.INFLUENCES; influence++) {
					int[] j = new int[4];
					j[influence] = joint;
					int packed = BoneAttributeCodec.packJoints(j[0], j[1], j[2], j[3]);

					lightInput[c * 2] = (packed & 0xFFFF) / 256.0f;
					lightInput[c * 2 + 1] = ((packed >>> 16) & 0xFFFF) / 256.0f;

					System.arraycopy(j, 0, expected, c * 4, 4);
					c++;
				}
			}

			int inBuffer = glGenBuffers();
			int outBuffer = glGenBuffers();
			try {
				glBindBuffer(GL_SHADER_STORAGE_BUFFER, inBuffer);
				glBufferData(GL_SHADER_STORAGE_BUFFER, lightInput, GL_STATIC_DRAW);
				glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, inBuffer);

				int[] outInit = new int[caseCount * 4];
				glBindBuffer(GL_SHADER_STORAGE_BUFFER, outBuffer);
				glBufferData(GL_SHADER_STORAGE_BUFFER, outInit, GL_STATIC_DRAW);
				glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, outBuffer);

				glUseProgram(program);
				glDispatchCompute((caseCount + 63) / 64, 1, 1);
				glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);

				int[] actual = new int[caseCount * 4];
				glBindBuffer(GL_SHADER_STORAGE_BUFFER, outBuffer);
				glGetBufferSubData(GL_SHADER_STORAGE_BUFFER, 0, actual);

				for (int i = 0; i < caseCount; i++) {
					int joint = i / BoneAttributeCodec.INFLUENCES;
					int influence = i % BoneAttributeCodec.INFLUENCES;
					for (int slot = 0; slot < 4; slot++) {
						assertThat(actual[i * 4 + slot])
								.as("joint %d placed in influence %d, decoded slot %d", joint, influence, slot)
								.isEqualTo(expected[i * 4 + slot]);
					}
				}
			} finally {
				glDeleteBuffers(inBuffer);
				glDeleteBuffers(outBuffer);
				glDeleteProgram(program);
			}
		}
	}

	@Test
	@DisplayName("the instance shader still routes through the shared skinning unit")
	void theInstanceShaderIncludesTheSharedSkinning() {
		String instanceShader = ShaderSources.read("assets/gemrender/flywheel/instance/skinned.vert");

		assertThat(instanceShader)
				.as("skinned.vert must include skin_lbs.glsl rather than carry its own copy")
				.contains("#include \"gemrender:skin_lbs.glsl\"")
				.contains("gemrender_skinMatrix(i.boneBase, flw_vertexLight, flw_vertexColor)");
	}
}
