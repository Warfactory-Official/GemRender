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
import static org.lwjgl.opengl.GL46C.glUseProgram;

import com.wf.gemrender.particle.ParticleBuffer;
import com.wf.gemrender.particle.ParticleCollision;
import com.wf.gemrender.particle.ParticleMotion;
import com.wf.gemrender.particle.ParticleStyle;
import org.joml.Vector3f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The GPU half of {@link com.wf.gemrender.particle.ParticleCollision} against the CPU half.
 *
 * <p>The contact is predicted in Java and drawn in GLSL, so the two evaluate the same closed form or the
 * particle visibly stops somewhere other than where the sweep said it would. Nothing in a screenshot would
 * tell you which one drifted, so this runs the shipped {@code particle.glsl} over a real texture buffer and
 * compares it against the Java the prediction was built on.
 */
@Tag("gl")
class ParticleContactGlTest {

	/** Not unit 0: an unassigned sampler reads unit 0, which would pass this test without the uniform. */
	private static final int SAMPLER_UNIT = 3;

	private static final float GRAVITY = 20.0f;

	private static String harness() {
		return """
				#version 460 core
				layout(local_size_x = 1) in;

				layout(std430, binding = 0) writeonly buffer Out {
				    float result[];
				};

				layout(location = 0) uniform float uAge;

				"""
				+ ShaderSources.particleGlsl()
				+ """

						void main() {
						    GemRenderParticle p = gemrender_particle(0u);
						    GemRenderStyle s = gemrender_style(p.style);

						    vec3 at = gemrender_particlePosition(p, s, uAge);
						    vec3 v = gemrender_particleVelocity(p, s, uAge);

						    result[0] = at.x;
						    result[1] = at.y;
						    result[2] = at.z;
						    result[3] = v.x;
						    result[4] = v.y;
						    result[5] = v.z;
						}
						""";
	}

	/** A floor: any segment crossing {@code y} on the way down lands on it. */
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

	/** The style region plus one particle, laid out the way {@link ParticleBuffer} lays them out. */
	private static float[] buffer(ParticleStyle style, Vector3f spawn, Vector3f velocity,
			ParticleCollision.Contact contact) {
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
		data[at + 10] = 0.0f;
		data[at + 11] = 1.0f;
		data[at + 12] = contact.contactAge();
		data[at + 13] = contact.normal();
		data[at + 14] = contact.restAge();
		return data;
	}

	private void assertAgrees(ParticleStyle style, Vector3f spawn, Vector3f velocity,
			ParticleCollision.Contact contact, String what) {
		float[] data = buffer(style, spawn, velocity, contact);

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
				glBufferData(GL_SHADER_STORAGE_BUFFER, new float[6], GL_STATIC_DRAW);
				glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, out);

				glUseProgram(program);
				HeadlessGl.samplerUnit(program, "_gemrender_particles", SAMPLER_UNIT);

				Vector3f expectedAt = new Vector3f();
				Vector3f expectedV = new Vector3f();

				for (float age = 0.0f; age < contact.life(); age += 0.05f) {
					glUniform1f(0, age);
					glDispatchCompute(1, 1, 1);
					glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);

					float[] actual = new float[6];
					glBindBuffer(GL_SHADER_STORAGE_BUFFER, out);
					glGetBufferSubData(GL_SHADER_STORAGE_BUFFER, 0, actual);

					ParticleCollision.positionAt(style, spawn, velocity, contact, age, expectedAt);
					ParticleCollision.velocityAt(style, velocity, contact, age, expectedV);

					String where = "%s at t=%.2f".formatted(what, age);
					assertThat(actual[0]).as("%s position x", where).isCloseTo(expectedAt.x, within(1e-3f));
					assertThat(actual[1]).as("%s position y", where).isCloseTo(expectedAt.y, within(1e-3f));
					assertThat(actual[2]).as("%s position z", where).isCloseTo(expectedAt.z, within(1e-3f));
					assertThat(actual[3]).as("%s velocity x", where).isCloseTo(expectedV.x, within(1e-3f));
					assertThat(actual[4]).as("%s velocity y", where).isCloseTo(expectedV.y, within(1e-3f));
					assertThat(actual[5]).as("%s velocity z", where).isCloseTo(expectedV.z, within(1e-3f));
				}
			} finally {
				glDeleteBuffers(out);
				glDeleteTextures(texture);
				glDeleteBuffers(texels);
				glDeleteProgram(program);
			}
		}
	}

	@Test
	@DisplayName("a particle that hits nothing draws exactly where it drew before contact existed")
	void nonCollidingParticleIsUnchanged() {
		ParticleStyle style = ParticleStyle.builder()
				.drag(ParticleStyle.dragFromPerTickFactor(0.9f))
				.gravity(GRAVITY)
				.build();

		Vector3f spawn = new Vector3f(1.0f, 12.0f, -2.0f);
		Vector3f velocity = new Vector3f(4.0f, 6.0f, -3.0f);
		ParticleCollision.Contact contact = ParticleCollision.none(2.0f);

		assertAgrees(style, spawn, velocity, contact, "free flight");

		// And the free-flight answer is still the one the original closed form gives, contact or no contact.
		Vector3f plain = ParticleMotion.position(style, spawn, velocity, 1.3f, new Vector3f());
		Vector3f through = ParticleCollision.positionAt(style, spawn, velocity, contact, 1.3f, new Vector3f());
		assertThat(through.x).isEqualTo(plain.x);
		assertThat(through.y).isEqualTo(plain.y);
		assertThat(through.z).isEqualTo(plain.z);
	}

	@Test
	@DisplayName("the shader stops a stopping particle where the sweep predicted, and holds it there")
	void stoppingParticleAgrees() {
		ParticleStyle style = ParticleStyle.builder()
				.gravity(GRAVITY)
				.stopsOnContact()
				.build();

		Vector3f spawn = new Vector3f(0.0f, 9.0f, 0.0f);
		Vector3f velocity = new Vector3f(2.5f, 1.0f, -1.5f);
		ParticleCollision.Contact contact = ParticleCollision.predict(floorAt(0.0), style,
				spawn.x, spawn.y, spawn.z, velocity.x, velocity.y, velocity.z, 3.0f, 0.0f);

		assertThat(contact.hits()).as("the fixture must actually reach the floor")
				.isTrue();
		assertAgrees(style, spawn, velocity, contact, "stop");
	}

	@Test
	@DisplayName("the shader rebounds a bouncing particle the way the sweep did, and settles where it did")
	void bouncingParticleAgrees() {
		ParticleStyle style = ParticleStyle.builder()
				.drag(ParticleStyle.dragFromPerTickFactor(0.98f))
				.gravity(GRAVITY)
				.bouncesOnContact(0.45f, 0.7f)
				.build();

		Vector3f spawn = new Vector3f(0.0f, 6.0f, 0.0f);
		Vector3f velocity = new Vector3f(3.0f, 2.0f, 0.0f);
		ParticleCollision.Contact contact = ParticleCollision.predict(floorAt(0.0), style,
				spawn.x, spawn.y, spawn.z, velocity.x, velocity.y, velocity.z, 4.0f, 0.0f);

		assertThat(contact.restAge()).as("the fixture must actually rebound and land again")
				.isGreaterThan(contact.contactAge());
		assertAgrees(style, spawn, velocity, contact, "bounce");
	}
}
