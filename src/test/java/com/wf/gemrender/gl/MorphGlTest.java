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
import static org.lwjgl.opengl.GL46C.glUniform1i;
import static org.lwjgl.opengl.GL46C.glUniform1ui;
import static org.lwjgl.opengl.GL46C.glUseProgram;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.wf.gemrender.gltf.GltfAnimation;
import com.wf.gemrender.gltf.GltfPose;
import com.wf.gemrender.gltf.MorphFixture;
import com.wf.gemrender.gltf.morph.GltfMorphLayout;
import com.wf.gemrender.gltf.morph.MorphTargets;
import com.wf.gemrender.render.BoneBuffer;
import com.wf.gemrender.render.MorphBuffer;

import java.util.Arrays;

@Tag("gl")
class MorphGlTest {

	private static String compute() {
		return """
				#version 460 core
				layout(local_size_x = 1) in;

				layout(binding = %d) uniform samplerBuffer _gemrender_bones;

				layout(std430, binding = 0) writeonly buffer Out {
				    float result[];
				};

				layout(location = 0) uniform uint uMorphBase;
				layout(location = 1) uniform int uMorphSet;
				layout(location = 2) uniform uint uVertexId;
				layout(location = 3) uniform vec3 uBasePosition;
				layout(location = 4) uniform vec3 uBaseNormal;

				%s

				void main() {
				    vec3 position = uBasePosition;
				    vec3 normal = uBaseNormal;
				    gemrender_applyMorph(_gemrender_bones, uMorphBase, uMorphSet, uVertexId, position, normal);
				    result[0] = position.x;
				    result[1] = position.y;
				    result[2] = position.z;
				    result[3] = normal.x;
				    result[4] = normal.y;
				    result[5] = normal.z;
				}
				""".formatted(BoneBuffer.TEXTURE_UNIT,
				ShaderSources.read("assets/gemrender/flywheel/morph.glsl"));
	}

	@Test
	@DisplayName("the shader's morph matches the CPU reference, for both sets and both strides")
	void morphMatchesTheCpuReference() {
		GltfMorphLayout layout = MorphFixture.morphLayout();
		MorphTargets bellows = MorphFixture.targets(MorphFixture.NODE_PUMP);
		MorphTargets piston = MorphFixture.targets(MorphFixture.NODE_PISTON);

		float[] deltas = new float[bellows.floatCount() + piston.floatCount()];
		System.arraycopy(bellows.deltas(), 0, deltas, 0, bellows.floatCount());
		System.arraycopy(piston.deltas(), 0, deltas, bellows.floatCount(), piston.floatCount());

		int morphBase = 32;
		float[] bones = new float[morphBase + layout.blockFloats()];
		float[] block = new float[layout.blockFloats()];
		samplePump(layout, block);
		System.arraycopy(block, 0, bones, morphBase, block.length);

		try (HeadlessGl gl = HeadlessGl.createOrSkip()) {
			int program = gl.computeProgram(compute());

			int morphBuffer = glGenBuffers();
			int morphTexture = glGenTextures();
			int boneBuffer = glGenBuffers();
			int boneTexture = glGenTextures();
			int outBuffer = glGenBuffers();
			try {
				bindTextureBuffer(morphBuffer, morphTexture, MorphBuffer.TEXTURE_UNIT, deltas);
				bindTextureBuffer(boneBuffer, boneTexture, BoneBuffer.TEXTURE_UNIT, bones);

				glBindBuffer(GL_SHADER_STORAGE_BUFFER, outBuffer);
				glBufferData(GL_SHADER_STORAGE_BUFFER, new float[6], GL_STATIC_DRAW);
				glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, outBuffer);

				glUseProgram(program);
				HeadlessGl.samplerUnit(program, "_gemrender_morphs", MorphBuffer.TEXTURE_UNIT);

				float[] bellowsWeights = MorphFixture.BELLOWS_WEIGHTS_AT_MID;
				float[] pistonWeights = MorphFixture.PISTON_WEIGHTS_AT_MID;

				assertSet(outBuffer, morphBase, 1, bellows, bellowsWeights,
						MorphFixture.positions(MorphFixture.NODE_PUMP), "bellows");
				assertSet(outBuffer, morphBase, 2, piston, pistonWeights,
						MorphFixture.positions(MorphFixture.NODE_PISTON), "piston");
			} finally {
				glDeleteBuffers(morphBuffer);
				glDeleteTextures(morphTexture);
				glDeleteBuffers(boneBuffer);
				glDeleteTextures(boneTexture);
				glDeleteBuffers(outBuffer);
				glDeleteProgram(program);
			}
		}
	}

	@Test
	@DisplayName("a morph set of zero leaves the vertex exactly alone")
	void setZeroIsTheEarlyOut() {
		try (HeadlessGl gl = HeadlessGl.createOrSkip()) {
			int program = gl.computeProgram(compute());

			int morphBuffer = glGenBuffers();
			int morphTexture = glGenTextures();
			int boneBuffer = glGenBuffers();
			int boneTexture = glGenTextures();
			int outBuffer = glGenBuffers();
			try {

				float[] poison = new float[64];
				Arrays.fill(poison, 999.0f);

				bindTextureBuffer(morphBuffer, morphTexture, MorphBuffer.TEXTURE_UNIT, poison);
				bindTextureBuffer(boneBuffer, boneTexture, BoneBuffer.TEXTURE_UNIT, poison);

				glBindBuffer(GL_SHADER_STORAGE_BUFFER, outBuffer);
				glBufferData(GL_SHADER_STORAGE_BUFFER, new float[6], GL_STATIC_DRAW);
				glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, outBuffer);

				glUseProgram(program);
				HeadlessGl.samplerUnit(program, "_gemrender_morphs", MorphBuffer.TEXTURE_UNIT);

				float[] result = dispatch(outBuffer, 0, 0, 0,
						new float[] { 1.5f, -2.5f, 3.5f }, new float[] { 0.0f, 1.0f, 0.0f });

				assertThat(result[0]).isEqualTo(1.5f);
				assertThat(result[1]).isEqualTo(-2.5f);
				assertThat(result[2]).isEqualTo(3.5f);
				assertThat(result[4]).isEqualTo(1.0f);
			} finally {
				glDeleteBuffers(morphBuffer);
				glDeleteTextures(morphTexture);
				glDeleteBuffers(boneBuffer);
				glDeleteTextures(boneTexture);
				glDeleteBuffers(outBuffer);
				glDeleteProgram(program);
			}
		}
	}

	@Test
	@DisplayName("the shipped instance shader still includes the file this verifies")
	void skinnedVertIncludesTheMorphShader() {
		String vert = ShaderSources.read("assets/gemrender/flywheel/instance/skinned.vert");

		assertThat(vert).contains("#include \"gemrender:morph.glsl\"");
		assertThat(vert).contains("gemrender_applyMorph(");
		assertThat(vert)
				.as("the morph set index is read out of the overlay attribute")
				.contains("int morphSet = flw_vertexOverlay.x;");
	}

	private static void samplePump(GltfMorphLayout layout, float[] block) {
		GltfAnimation clip = MorphFixture.animation();
		org.joml.Matrix4f[] palette = new org.joml.Matrix4f[MorphFixture.layout()
				.size()];
		for (int i = 0; i < palette.length; i++) {
			palette[i] = new org.joml.Matrix4f();
		}
		GltfPose.evaluate(MorphFixture.layout(), clip, MorphFixture.MID_CLIP, palette, layout, block);
	}

	private static void assertSet(int outBuffer, int morphBase, int morphSet, MorphTargets targets,
			float[] weights, float[] base, String name) {
		for (int v = 0; v < targets.vertexCount(); v++) {
			float[] expected = { base[v * 3], base[v * 3 + 1], base[v * 3 + 2] };
			targets.applyPosition(weights, v, expected);

			float[] actual = dispatch(outBuffer, morphBase, morphSet, v,
					new float[] { base[v * 3], base[v * 3 + 1], base[v * 3 + 2] },
					new float[] { 0.0f, 1.0f, 0.0f });

			for (int c = 0; c < 3; c++) {
				assertThat(actual[c])
						.as("%s vertex %d, component %d", name, v, c)
						.isCloseTo(expected[c], within(1e-4f));
			}
		}
	}

	private static float[] dispatch(int outBuffer, int morphBase, int morphSet, int vertexId,
			float[] position, float[] normal) {
		glUniform1ui(0, morphBase);
		glUniform1i(1, morphSet);
		glUniform1ui(2, vertexId);
		org.lwjgl.opengl.GL46C.glUniform3f(3, position[0], position[1], position[2]);
		org.lwjgl.opengl.GL46C.glUniform3f(4, normal[0], normal[1], normal[2]);

		glDispatchCompute(1, 1, 1);
		glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);

		float[] result = new float[6];
		glBindBuffer(GL_SHADER_STORAGE_BUFFER, outBuffer);
		glGetBufferSubData(GL_SHADER_STORAGE_BUFFER, 0, result);
		return result;
	}

	private static void bindTextureBuffer(int buffer, int texture, int unit, float[] contents) {
		glBindBuffer(GL_TEXTURE_BUFFER, buffer);
		glBufferData(GL_TEXTURE_BUFFER, contents, GL_STATIC_DRAW);

		glActiveTexture(GL_TEXTURE0 + unit);
		glBindTexture(GL_TEXTURE_BUFFER, texture);
		org.lwjgl.opengl.GL46C.glTexBuffer(GL_TEXTURE_BUFFER, GL_R32F, buffer);
	}
}
