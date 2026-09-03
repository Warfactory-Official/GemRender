package com.wf.gemrender.gl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.lwjgl.opengl.GL46C.GL_R32F;
import static org.lwjgl.opengl.GL46C.GL_SHADER_STORAGE_BARRIER_BIT;
import static org.lwjgl.opengl.GL46C.GL_SHADER_STORAGE_BUFFER;
import static org.lwjgl.opengl.GL46C.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL46C.GL_TEXTURE0;
import static org.lwjgl.opengl.GL46C.GL_TEXTURE_BUFFER;
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
import static org.lwjgl.opengl.GL46C.glMemoryBarrier;
import static org.lwjgl.opengl.GL46C.glTexBuffer;
import static org.lwjgl.opengl.GL46C.glUniform1ui;
import static org.lwjgl.opengl.GL46C.glUniform2f;
import static org.lwjgl.opengl.GL46C.glUniform4f;
import static org.lwjgl.opengl.GL46C.glUseProgram;

import org.joml.Matrix4f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.wf.gemrender.gltf.MatrixScalar;
import com.wf.gemrender.gltf.skin.BoneAttributeCodec;
import com.wf.gemrender.render.BoneBuffer;

@Tag("gl")
class SkinningBlendGlTest {
	private static final String SKIN_LBS = "assets/gemrender/flywheel/skin_lbs.glsl";

	private static String harness() {
		return """
				#version 460 core
				layout(local_size_x = 1) in;

				layout(std430, binding = 0) writeonly buffer Out {
				    float result[];
				};

				layout(location = 0) uniform uint uBase;
				layout(location = 1) uniform vec2 uPackedJoints;
				layout(location = 2) uniform vec4 uWeights;

				"""
				+ ShaderSources.read(SKIN_LBS)
				+ """

						void main() {
						    mat4 m = gemrender_skinMatrix(uBase, uPackedJoints, uWeights);
						    for (int col = 0; col < 4; col++) {
						        for (int row = 0; row < 4; row++) {
						            result[col * 4 + row] = m[col][row];
						        }
						    }
						}
						""";
	}

	private static Matrix4f[] palette() {
		Matrix4f[] palette = new Matrix4f[BoneAttributeCodec.MAX_JOINTS];
		for (int i = 0; i < palette.length; i++) {
			palette[i] = new Matrix4f()
					.translation(i * 0.25f, 1.0f - i * 0.5f, i * -0.125f)
					.rotateY(i * 0.05f)
					.rotateX(0.1f)
					.scale(1.0f + i * 0.01f, 1.0f, 1.0f);
		}
		return palette;
	}

	@Test
	@DisplayName("the shipped skinning shader blends exactly as the CPU reference does")
	void blendMatchesTheCpuReference() {
		Matrix4f[] palette = palette();

		int[][] joints = {
				{ 0, 0, 0, 0 },
				{ 1, 2, 0, 0 },
				{ 3, 5, 7, 11 },
				{ 128, 200, 255, 129 },
				{ 254, 1, 255, 0 },
		};
		float[][] weights = {
				{ 1.0f, 0.0f, 0.0f, 0.0f },
				{ 0.5f, 0.5f, 0.0f, 0.0f },
				{ 0.4f, 0.3f, 0.2f, 0.1f },
				{ 0.25f, 0.25f, 0.25f, 0.25f },
				{ 0.7f, 0.15f, 0.15f, 0.0f },
		};

		try (HeadlessGl gl = HeadlessGl.createOrSkip()) {
			int program = gl.computeProgram(harness());

			float[] upload = new float[palette.length * BoneBuffer.FLOATS_PER_MATRIX];
			for (int i = 0; i < palette.length; i++) {
				palette[i].get(upload, i * BoneBuffer.FLOATS_PER_MATRIX);
			}

			int paletteBuffer = glGenBuffers();
			int paletteTexture = glGenTextures();
			int outBuffer = glGenBuffers();
			try {
				glBindBuffer(GL_TEXTURE_BUFFER, paletteBuffer);
				glBufferData(GL_TEXTURE_BUFFER, upload, GL_STATIC_DRAW);

				glActiveTexture(GL_TEXTURE0 + BoneBuffer.TEXTURE_UNIT);
				glBindTexture(GL_TEXTURE_BUFFER, paletteTexture);
				glTexBuffer(GL_TEXTURE_BUFFER, GL_R32F, paletteBuffer);

				glBindBuffer(GL_SHADER_STORAGE_BUFFER, outBuffer);
				glBufferData(GL_SHADER_STORAGE_BUFFER, new float[16], GL_STATIC_DRAW);
				glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, outBuffer);

				glUseProgram(program);
				HeadlessGl.samplerUnit(program, "_gemrender_bones", BoneBuffer.TEXTURE_UNIT);

				for (int c = 0; c < joints.length; c++) {
					assertBlend(outBuffer, palette, joints[c], weights[c], "case " + c);
				}
			} finally {
				glDeleteBuffers(paletteBuffer);
				glDeleteTextures(paletteTexture);
				glDeleteBuffers(outBuffer);
				glDeleteProgram(program);
			}
		}
	}

	private static void assertBlend(int outBuffer, Matrix4f[] palette, int[] joints, float[] weights,
			String description) {
		int[] quantised = new int[BoneAttributeCodec.INFLUENCES];
		BoneAttributeCodec.quantizeWeights(weights, 0, quantised);

		float[] channels = new float[BoneAttributeCodec.INFLUENCES];
		for (int i = 0; i < channels.length; i++) {
			channels[i] = BoneAttributeCodec.weightChannel(quantised[i]);
		}

		int packed = BoneAttributeCodec.packJoints(joints[0], joints[1], joints[2], joints[3]);

		glUniform1ui(0, 0);
		glUniform2f(1, (packed & 0xFFFF) / 256.0f, ((packed >>> 16) & 0xFFFF) / 256.0f);
		glUniform4f(2, channels[0], channels[1], channels[2], channels[3]);

		glDispatchCompute(1, 1, 1);
		glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);

		float[] actual = new float[16];
		glBindBuffer(GL_SHADER_STORAGE_BUFFER, outBuffer);
		glGetBufferSubData(GL_SHADER_STORAGE_BUFFER, 0, actual);

		Matrix4f expected = new Matrix4f().zero();
		for (int i = 0; i < BoneAttributeCodec.INFLUENCES; i++) {
			if (channels[i] <= 0.0f) {
				continue;
			}
			expected.add(MatrixScalar.times(palette[joints[i]], channels[i]));
		}

		float[] want = new float[16];
		expected.get(want);
		for (int i = 0; i < 16; i++) {
			assertThat(actual[i])
					.as("%s (joints %s), element %d", description, java.util.Arrays.toString(joints), i)
					.isEqualTo(want[i], within(1e-4f));
		}
	}

	@Test
	@DisplayName("a joint index above 127 survives the trip through the light attribute")
	void highJointIndicesSurviveTheEncoding() {
		int packed = BoneAttributeCodec.packJoints(200, 255, 128, 129);
		assertThat(BoneAttributeCodec.unpackJoint(packed, 0)).isEqualTo(200);
		assertThat(BoneAttributeCodec.unpackJoint(packed, 1)).isEqualTo(255);
		assertThat(BoneAttributeCodec.unpackJoint(packed, 2)).isEqualTo(128);
		assertThat(BoneAttributeCodec.unpackJoint(packed, 3)).isEqualTo(129);
		Matrix4f[] palette = palette();
		try (HeadlessGl gl = HeadlessGl.createOrSkip()) {
			int program = gl.computeProgram(harness());

			float[] upload = new float[palette.length * BoneBuffer.FLOATS_PER_MATRIX];
			for (int i = 0; i < palette.length; i++) {
				palette[i].get(upload, i * BoneBuffer.FLOATS_PER_MATRIX);
			}

			int paletteBuffer = glGenBuffers();
			int paletteTexture = glGenTextures();
			int outBuffer = glGenBuffers();
			try {
				glBindBuffer(GL_TEXTURE_BUFFER, paletteBuffer);
				glBufferData(GL_TEXTURE_BUFFER, upload, GL_STATIC_DRAW);

				glActiveTexture(GL_TEXTURE0 + BoneBuffer.TEXTURE_UNIT);
				glBindTexture(GL_TEXTURE_BUFFER, paletteTexture);
				glTexBuffer(GL_TEXTURE_BUFFER, GL_R32F, paletteBuffer);

				glBindBuffer(GL_SHADER_STORAGE_BUFFER, outBuffer);
				glBufferData(GL_SHADER_STORAGE_BUFFER, new float[16], GL_STATIC_DRAW);
				glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, outBuffer);

				glUseProgram(program);
				HeadlessGl.samplerUnit(program, "_gemrender_bones", BoneBuffer.TEXTURE_UNIT);
				assertBlend(outBuffer, palette, new int[] { 200, 0, 0, 0 },
						new float[] { 1.0f, 0.0f, 0.0f, 0.0f }, "joint 200 alone");
			} finally {
				glDeleteBuffers(paletteBuffer);
				glDeleteTextures(paletteTexture);
				glDeleteBuffers(outBuffer);
				glDeleteProgram(program);
			}
		}
	}
}
