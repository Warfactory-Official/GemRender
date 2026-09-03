package com.wf.gemrender.gl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.lwjgl.opengl.GL46C.GL_SHADER_STORAGE_BARRIER_BIT;
import static org.lwjgl.opengl.GL46C.GL_SHADER_STORAGE_BUFFER;
import static org.lwjgl.opengl.GL46C.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL46C.GL_TEXTURE_BUFFER;
import static org.lwjgl.opengl.GL46C.GL_R32F;
import static org.lwjgl.opengl.GL46C.GL_TEXTURE0;
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
import static org.lwjgl.opengl.GL46C.glUseProgram;

import org.joml.Matrix4f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.wf.gemrender.render.BoneBuffer;

@Tag("gl")
class BonePaletteGlTest {

	private static final String FETCH_COMPUTE = """
			#version 460 core
			layout(local_size_x = 1) in;

			const int GEMRENDER_FLOATS_PER_MATRIX = 16;

			layout(binding = %d) uniform samplerBuffer _gemrender_bones;

			layout(std430, binding = 0) writeonly buffer Out {
			    float result[];
			};

			layout(location = 0) uniform uint uBase;
			layout(location = 1) uniform int uJoint;

			mat4 gemrender_boneMatrix(uint base, int joint) {
			    int offset = (int(base) + joint) * GEMRENDER_FLOATS_PER_MATRIX;

			    return mat4(
			        texelFetch(_gemrender_bones, offset +  0).r, texelFetch(_gemrender_bones, offset +  1).r,
			        texelFetch(_gemrender_bones, offset +  2).r, texelFetch(_gemrender_bones, offset +  3).r,
			        texelFetch(_gemrender_bones, offset +  4).r, texelFetch(_gemrender_bones, offset +  5).r,
			        texelFetch(_gemrender_bones, offset +  6).r, texelFetch(_gemrender_bones, offset +  7).r,
			        texelFetch(_gemrender_bones, offset +  8).r, texelFetch(_gemrender_bones, offset +  9).r,
			        texelFetch(_gemrender_bones, offset + 10).r, texelFetch(_gemrender_bones, offset + 11).r,
			        texelFetch(_gemrender_bones, offset + 12).r, texelFetch(_gemrender_bones, offset + 13).r,
			        texelFetch(_gemrender_bones, offset + 14).r, texelFetch(_gemrender_bones, offset + 15).r
			    );
			}

			void main() {
			    mat4 m = gemrender_boneMatrix(uBase, uJoint);
			    // Write back column-major so the comparison against JOML is unambiguous.
			    for (int col = 0; col < 4; col++) {
			        for (int row = 0; row < 4; row++) {
			            result[col * 4 + row] = m[col][row];
			        }
			    }
			}
			""".formatted(BoneBuffer.TEXTURE_UNIT);

	@Test
	@DisplayName("a palette entry round-trips through the texture buffer at its boneBase offset")
	void paletteFetchMatchesUpload() {
		try (HeadlessGl gl = HeadlessGl.createOrSkip()) {
			int program = gl.computeProgram(FETCH_COMPUTE);

			Matrix4f[] palette = {
					new Matrix4f().identity(),
					new Matrix4f().translation(1.0f, 2.0f, 3.0f).rotateX(0.5f),
					new Matrix4f().translation(-7.0f, 11.0f, 0.25f).scale(2.0f, 3.0f, 4.0f),
			};

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

				for (int i = 0; i < palette.length; i++) {
					assertFetch(program, outBuffer, 0, i, palette[i], "base=0 joint=" + i);
					assertFetch(program, outBuffer, i, 0, palette[i], "base=" + i + " joint=0");
				}
			} finally {
				glDeleteBuffers(paletteBuffer);
				glDeleteTextures(paletteTexture);
				glDeleteBuffers(outBuffer);
				glDeleteProgram(program);
			}
		}
	}

	private static void assertFetch(int program, int outBuffer, int base, int joint,
			Matrix4f expected, String description) {
		org.lwjgl.opengl.GL46C.glUniform1ui(0, base);
		org.lwjgl.opengl.GL46C.glUniform1i(1, joint);

		glDispatchCompute(1, 1, 1);
		glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);

		float[] actual = new float[16];
		glBindBuffer(GL_SHADER_STORAGE_BUFFER, outBuffer);
		glGetBufferSubData(GL_SHADER_STORAGE_BUFFER, 0, actual);

		float[] want = new float[16];
		expected.get(want);

		for (int i = 0; i < 16; i++) {
			assertThat(actual[i])
					.as("%s, element %d", description, i)
					.isEqualTo(want[i], org.assertj.core.api.Assertions.within(1e-5f));
		}
	}
}
