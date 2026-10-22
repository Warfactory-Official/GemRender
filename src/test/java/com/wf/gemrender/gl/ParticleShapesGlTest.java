package com.wf.gemrender.gl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.lwjgl.opengl.GL46C.GL_RGBA32F;
import static org.lwjgl.opengl.GL46C.GL_SHADER_STORAGE_BARRIER_BIT;
import static org.lwjgl.opengl.GL46C.GL_SHADER_STORAGE_BUFFER;
import static org.lwjgl.opengl.GL46C.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL46C.GL_TEXTURE3;
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
import static org.lwjgl.opengl.GL46C.glUniform1f;
import static org.lwjgl.opengl.GL46C.glUniform3f;
import static org.lwjgl.opengl.GL46C.glUseProgram;

import com.wf.gemrender.particle.ParticleBuffer;
import com.wf.gemrender.particle.ParticleCollision;
import com.wf.gemrender.particle.ParticleLook;
import com.wf.gemrender.particle.ParticleMotion;
import com.wf.gemrender.particle.ParticleShapes;
import com.wf.gemrender.particle.ParticleStyle;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** The shipped {@code particle.glsl} shape and look functions against {@link ParticleShapes} / {@link ParticleLook}. */
@Tag("gl")
class ParticleShapesGlTest {

	private static final int SAMPLER_UNIT = 3;

	private static final int OUTPUTS = 31;

	private static final Vector3f EYE = new Vector3f(-4.0f, 7.0f, 9.0f);

	private static final Vector3f RIGHT = new Vector3f(1.0f, 0.0f, 0.0f);

	private static final float SIZE = 0.3f;

	/** Ramp 4.5..13.5 blocks: the fixtures' 9..13 from {@link #EYE} cross it. */
	private static final float NEAR_SIZE = 9.0f;

	/** Ramp CULL..CULL + 2: crossed by the fixtures as for {@link #NEAR_SIZE}. */
	private static final float CULL = 10.0f;

	private static String harness() {
		return """
				#version 460 core
				layout(local_size_x = 1) in;

				layout(std430, binding = 0) writeonly buffer Out {
				    float result[];
				};

				layout(location = 0) uniform float uAge;
				layout(location = 1) uniform vec3 uEye;

				"""
				+ "const float NEAR_SIZE = " + NEAR_SIZE + ";\nconst float CULL = " + CULL + ";\n"
				+ ShaderSources.particleGlsl()
				+ """

						void put(int at, vec3 v) {
						    result[at] = v.x;
						    result[at + 1] = v.y;
						    result[at + 2] = v.z;
						}

						void main() {
						    GemRenderParticle p = gemrender_particle(0u);
						    GemRenderStyle s = gemrender_style(p.style);
						    float unitAge = gemrender_particleUnitAge(p, uAge);

						    put(0, gemrender_decalCorner(p, vec2(-0.5, -0.5), 0.3));
						    put(3, gemrender_decalCorner(p, vec2(0.5, 0.5), 0.3));

						    mat3 body = gemrender_bodyBasis(p, s, uAge);
						    put(6, body[0]);
						    put(9, body[1]);
						    put(12, body[2]);

						    vec4 color = gemrender_particleColor(p, s, unitAge);
						    put(15, color.rgb);
						    result[18] = color.a;

						    vec2 light = gemrender_particleLight(p, s);
						    result[19] = light.x;
						    result[20] = light.y;

						    vec3 center = gemrender_particlePosition(p, s, uAge);
						    vec3 velocity = gemrender_particleMotion(p, s, uAge);
						    put(21, gemrender_streakCorner(center, velocity, uEye, vec3(1.0, 0.0, 0.0), vec2(-0.5, -0.5),
						                                   0.3, s.streak));
						    put(24, gemrender_streakCorner(center, velocity, uEye, vec3(1.0, 0.0, 0.0), vec2(0.5, 0.5),
						                                   0.3, s.streak));
						    result[27] = gemrender_bodyScale(s, unitAge);
						    result[28] = gemrender_nearFade(center, uEye, NEAR_SIZE);
						    result[29] = gemrender_particleHeat(s, unitAge);
						    result[30] = gemrender_cullFade(center, uEye, CULL);
						}
						""";
	}

	private static ParticleCollision.Probe floorAt(double y) {
		return (fx, fy, fz, tx, ty, tz, into) -> {
			if (fy < y || ty >= y) {
				return false;
			}
			into.fraction = (float) ((fy - y) / (fy - ty));
			into.normal = ParticleCollision.PLUS_Y;
			return true;
		};
	}

	private static float[] buffer(ParticleStyle style, Vector3f spawn, Vector3f velocity, float spin, float tintScale,
			ParticleCollision.Contact contact, int look) {
		float[] data = new float[ParticleBuffer.STYLE_REGION_FLOATS + ParticleBuffer.PARTICLE_FLOATS];
		style.write(data, 0);

		int at = ParticleBuffer.STYLE_REGION_FLOATS;
		data[at] = spawn.x;
		data[at + 1] = spawn.y;
		data[at + 2] = spawn.z;
		data[at + 3] = 0.0f;
		data[at + 4] = velocity.x;
		data[at + 5] = velocity.y;
		data[at + 6] = velocity.z;
		data[at + 7] = contact.life();
		data[at + 8] = 0.0f;
		data[at + 9] = 1.0f;
		data[at + 10] = spin;
		data[at + 11] = tintScale;
		data[at + 12] = contact.contactAge();
		data[at + 13] = contact.normal();
		data[at + 14] = contact.restAge();
		data[at + 15] = look;
		return data;
	}

	private static void close(float actual, float expected, float tolerance, String what) {
		assertThat(actual).as(what).isCloseTo(expected, within(tolerance));
	}

	private static void close(float[] actual, int at, Vector3f expected, float tolerance, String what) {
		close(actual[at], expected.x, tolerance, what + ".x");
		close(actual[at + 1], expected.y, tolerance, what + ".y");
		close(actual[at + 2], expected.z, tolerance, what + ".z");
	}

	private void assertAgrees(ParticleStyle style, Vector3f spawn, Vector3f velocity, float spin, float tintScale,
			ParticleCollision.Contact contact, int look, String what) {
		float[] data = buffer(style, spawn, velocity, spin, tintScale, contact, look);

		try (HeadlessGl gl = HeadlessGl.createOrSkip()) {
			int program = gl.computeProgram(harness());
			int texels = glGenBuffers();
			int texture = glGenTextures();
			int out = glGenBuffers();
			try {
				glBindBuffer(GL_TEXTURE_BUFFER, texels);
				glBufferData(GL_TEXTURE_BUFFER, data, GL_STATIC_DRAW);

				glActiveTexture(GL_TEXTURE3);
				glBindTexture(GL_TEXTURE_BUFFER, texture);
				glTexBuffer(GL_TEXTURE_BUFFER, GL_RGBA32F, texels);

				glBindBuffer(GL_SHADER_STORAGE_BUFFER, out);
				glBufferData(GL_SHADER_STORAGE_BUFFER, new float[OUTPUTS], GL_STATIC_DRAW);
				glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, out);

				glUseProgram(program);
				HeadlessGl.samplerUnit(program, "_gemrender_particles", SAMPLER_UNIT);
				glUniform3f(1, EYE.x, EYE.y, EYE.z);

				Matrix3f body = new Matrix3f();
				Vector3f at = new Vector3f();
				Vector3f v = new Vector3f();

				int ramping = 0;
				for (float age = 0.0f; age < contact.life(); age += 0.05f) {
					glUniform1f(0, age);
					glDispatchCompute(1, 1, 1);
					glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);

					float[] actual = new float[OUTPUTS];
					glBindBuffer(GL_SHADER_STORAGE_BUFFER, out);
					glGetBufferSubData(GL_SHADER_STORAGE_BUFFER, 0, actual);

					String when = "%s at t=%.2f".formatted(what, age);
					float unitAge = ParticleMotion.unitAge(age, contact.life());

					close(actual, 0, ParticleShapes.decalCorner(spawn, velocity, spin, -0.5f, -0.5f, SIZE,
							new Vector3f()), 1e-4f, when + " decal corner 0");
					close(actual, 3, ParticleShapes.decalCorner(spawn, velocity, spin, 0.5f, 0.5f, SIZE,
							new Vector3f()), 1e-4f, when + " decal corner 2");

					ParticleShapes.bodyBasis(style, velocity, spin, contact, age, body);
					close(actual, 6, body.getColumn(0, new Vector3f()), 2e-3f, when + " body X");
					close(actual, 9, body.getColumn(1, new Vector3f()), 2e-3f, when + " body Y");
					close(actual, 12, body.getColumn(2, new Vector3f()), 2e-3f, when + " body Z");

					float cool = ParticleMotion.cool(style, unitAge);
					float r = look == ParticleLook.NONE ? 1.0f : ((look - 1) >> 19 & 31) / 31.0f;
					float g = look == ParticleLook.NONE ? 1.0f : ((look - 1) >> 13 & 63) / 63.0f;
					float b = look == ParticleLook.NONE ? 1.0f : ((look - 1) >> 8 & 31) / 31.0f;
					close(actual[15], Math.min(1.0f, style.tintRed * r * cool * tintScale), 1e-4f, when + " red");
					close(actual[16], Math.min(1.0f, style.tintGreen * g * cool * tintScale), 1e-4f, when + " green");
					close(actual[17], Math.min(1.0f, style.tintBlue * b * cool * tintScale), 1e-4f, when + " blue");
					close(actual[18], ParticleMotion.alpha(style, unitAge), 1e-4f, when + " alpha");

					close(actual[19], look == ParticleLook.NONE ? style.lightBlock
							: ParticleLook.blockLight(look) / 16.0f, 1e-6f, when + " block light");
					close(actual[20], look == ParticleLook.NONE ? style.lightSky
							: ParticleLook.skyLight(look) / 16.0f, 1e-6f, when + " sky light");

					ParticleCollision.positionAt(style, spawn, velocity, contact, age, at);
					ParticleCollision.motionAt(style, velocity, contact, age, v);
					close(actual, 21, ParticleShapes.streakCorner(at, v, EYE, RIGHT, -0.5f, -0.5f, SIZE, style.streak,
							new Vector3f()), 2e-3f, when + " streak tail");
					close(actual, 24, ParticleShapes.streakCorner(at, v, EYE, RIGHT, 0.5f, 0.5f, SIZE, style.streak,
							new Vector3f()), 2e-3f, when + " streak head");
					close(actual[27], ParticleShapes.bodyScale(style, unitAge), 1e-5f, when + " body scale");
					close(actual[28], ParticleShapes.nearFade(at, EYE, NEAR_SIZE), 1e-4f, when + " near fade");
					if (actual[28] > 0.0f && actual[28] < 1.0f) {
						ramping++;
					}
					close(actual[29], ParticleMotion.heat(style, unitAge), 1e-5f, when + " heat");
					close(actual[30], ParticleShapes.cullFade(at, EYE, CULL), 1e-4f, when + " cull fade");
				}
				assertThat(ramping).as(what + ": frames inside the near-fade ramp").isPositive();
			} finally {
				glDeleteBuffers(out);
				glDeleteTextures(texture);
				glDeleteBuffers(texels);
				glDeleteProgram(program);
			}
		}
	}

	@Test
	@DisplayName("a bouncing, tumbling, fading, tinted and lit particle draws where Java says, in the colour it says")
	void everyShapeAgreesThroughABounce() {
		ParticleStyle style = ParticleStyle.builder()
				.gravity(20.0f)
				.spin(17.0f)
				.tint(0.9f, 0.8f, 0.7f)
				.alpha(0.9f, 1.5f)
				.cool(0.3f, 0.5f)
				.glow(0.7f)
				.fadeOut(0.6f)
				.streak(0.03f)
				.bouncesOnContact(0.35f, 0.6f)
				.build();
		Vector3f spawn = new Vector3f(0.5f, 1.6f, -0.25f);
		Vector3f velocity = new Vector3f(2.2f, 3.1f, -1.4f);
		ParticleCollision.Contact contact = ParticleCollision.predict(floorAt(0.0), style, spawn.x, spawn.y, spawn.z,
				velocity.x, velocity.y, velocity.z, 3.0f, 0.02f);

		assertThat(contact.restAge()).as("fixture rebounds and lands").isGreaterThan(contact.contactAge());
		assertAgrees(style, spawn, velocity, 1.3f, 0.95f, contact, ParticleLook.of(0x8A5C2E, 11, 3), "bounce");
	}

	@Test
	@DisplayName("a stopping particle with no look keeps the style tint and light")
	void noLookKeepsTheStyle() {
		ParticleStyle style = ParticleStyle.builder()
				.gravity(20.0f)
				.spin(9.0f)
				.tint(0.4f, 0.5f, 0.6f)
				.light(0.25f, 0.75f)
				.stopsOnContact()
				.build();
		Vector3f spawn = new Vector3f(0.0f, 2.0f, 0.0f);
		Vector3f velocity = new Vector3f(-1.0f, 0.5f, 2.0f);
		ParticleCollision.Contact contact = ParticleCollision.predict(floorAt(0.0), style, spawn.x, spawn.y, spawn.z,
				velocity.x, velocity.y, velocity.z, 2.0f, 0.02f);

		assertThat(contact.hits()).isTrue();
		assertAgrees(style, spawn, velocity, 4.0f, 1.0f, contact, ParticleLook.NONE, "stop");
	}

	@Test
	@DisplayName("a free-flying particle tumbles and streaks the same on both sides")
	void freeFlightAgrees() {
		ParticleStyle style = ParticleStyle.builder()
				.drag(ParticleStyle.dragFromPerTickFactor(0.92f))
				.gravity(8.0f)
				.spin(-6.0f)
				.streak(0.05f)
				.build();
		assertAgrees(style, new Vector3f(1.0f, 5.0f, 2.0f), new Vector3f(12.0f, 4.0f, -7.0f), 0.4f, 1.0f,
				ParticleCollision.none(1.5f), ParticleLook.of(0xFFFFFF), "free");
	}
}
