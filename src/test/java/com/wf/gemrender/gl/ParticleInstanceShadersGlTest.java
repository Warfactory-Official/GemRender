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
import static org.lwjgl.opengl.GL46C.glUniform1i;
import static org.lwjgl.opengl.GL46C.glUniform3f;
import static org.lwjgl.opengl.GL46C.glUniform4f;
import static org.lwjgl.opengl.GL46C.glUniformMatrix4fv;
import static org.lwjgl.opengl.GL46C.glUseProgram;

import com.wf.gemrender.particle.ParticleBuffer;
import com.wf.gemrender.particle.ParticleCollision;
import com.wf.gemrender.particle.ParticleLook;
import com.wf.gemrender.particle.ParticleMotion;
import com.wf.gemrender.particle.ParticleShapes;
import com.wf.gemrender.particle.ParticleStyle;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The shipped DECAL, STREAK and BODY instance vertex and cull shaders, run under a transcribed Flywheel
 * stage, against {@link ParticleShapes}. Cull: every drawn vertex inside the sphere; dead => negative radius.
 */
@Tag("gl")
class ParticleInstanceShadersGlTest {
	private static final String ROOT = "assets/gemrender/flywheel/";

	private static final int SAMPLER_UNIT = 3;

	private static final int VERTEX_FLOATS = 12;

	private static final int MAX_VERTICES = 8;

	private static final Vector3f ORIGIN = new Vector3f(-3.0f, 2.0f, 5.0f);

	private static final Vector3f CAMERA = new Vector3f(-6.0f, 4.5f, -2.0f);

	private static final Matrix4f VIEW_INVERSE = new Matrix4f().translation(CAMERA)
			.rotateY(0.7f)
			.rotateX(-0.3f);

	/** Quad: ParticleQuad corners, sphere (0, 0, 0, sqrt(1/2)). */
	private static final float[][] QUAD = {{-0.5f, -0.5f, 0, 0, 0, 1}, {0.5f, -0.5f, 0, 0, 0, 1},
			{0.5f, 0.5f, 0, 0, 0, 1}, {-0.5f, 0.5f, 0, 0, 0, 1}};

	private static final Vector4f QUAD_SPHERE = new Vector4f(0.0f, 0.0f, 0.0f, 0.70710678f);

	/** Casing-like box, long axis +Y, off-centre sphere. */
	private static final float[][] BOX = box(0.03f, 0.1f, 0.02f, 0.01f);

	private static final Vector4f BOX_SPHERE = new Vector4f(0.0f, 0.01f, 0.0f,
			(float) Math.sqrt(0.03 * 0.03 + 0.1 * 0.1 + 0.02 * 0.02));

	private static float[][] box(float hx, float hy, float hz, float cy) {
		float[][] out = new float[8][];
		for (int i = 0; i < 8; i++) {
			float sx = (i & 1) == 0 ? -1 : 1;
			float sy = (i & 2) == 0 ? -1 : 1;
			float sz = (i & 4) == 0 ? -1 : 1;
			Vector3f n = new Vector3f(sx, sy, sz).normalize();
			out[i] = new float[]{sx * hx, cy + sy * hy, sz * hz, n.x, n.y, n.z};
		}
		return out;
	}

	private static String strip(String path) {
		return ShaderSources.read(ROOT + path)
				.lines()
				.filter(line -> !line.stripLeading()
						.startsWith("#include"))
				.reduce("", (a, b) -> a + b + "\n");
	}

	private static String harness(String type) {
		return """
				#version 460 core
				layout(local_size_x = 1) in;

				layout(std430, binding = 0) writeonly buffer Out {
				    float result[];
				};

				layout(std430, binding = 1) readonly buffer In {
				    float vertices[];
				};

				// Flywheel stage, transcribed: uniforms and the vertex globals the instance shader writes.
				layout(location = 0) uniform float flw_renderSeconds;
				layout(location = 1) uniform vec3 flw_cameraPos;
				layout(location = 2) uniform mat4 flw_viewInverse;
				layout(location = 3) uniform vec3 uOrigin;
				layout(location = 4) uniform int uVertices;
				layout(location = 5) uniform vec4 uSphere;

				// GemRenderParticleTypes layout.
				struct FlwInstance {
				    vec3 origin;
				    uint particle;
				};

				vec4 flw_vertexPos;
				vec3 flw_vertexNormal;
				vec4 flw_vertexColor;
				ivec2 flw_vertexOverlay;
				vec2 flw_vertexLight;

				"""
				+ ShaderSources.particleGlsl()
				+ strip("instance/" + type + ".vert")
				+ strip("instance/cull/" + type + ".glsl")
				+ """

						void main() {
						    FlwInstance i;
						    i.origin = uOrigin;
						    i.particle = 0u;

						    for (int v = 0; v < uVertices; v++) {
						        flw_vertexPos = vec4(vertices[v * 6], vertices[v * 6 + 1], vertices[v * 6 + 2], 1.0);
						        flw_vertexNormal = vec3(vertices[v * 6 + 3], vertices[v * 6 + 4], vertices[v * 6 + 5]);
						        flw_instanceVertex(i);
						        int at = v * 12;
						        result[at] = flw_vertexPos.x;
						        result[at + 1] = flw_vertexPos.y;
						        result[at + 2] = flw_vertexPos.z;
						        result[at + 3] = flw_vertexNormal.x;
						        result[at + 4] = flw_vertexNormal.y;
						        result[at + 5] = flw_vertexNormal.z;
						        result[at + 6] = flw_vertexColor.r;
						        result[at + 7] = flw_vertexColor.g;
						        result[at + 8] = flw_vertexColor.b;
						        result[at + 9] = flw_vertexColor.a;
						        result[at + 10] = flw_vertexLight.x;
						        result[at + 11] = flw_vertexLight.y;
						    }

						    vec3 center = uSphere.xyz;
						    float radius = uSphere.w;
						    flw_transformBoundingSphere(i, center, radius);
						    int at = 8 * 12;
						    result[at] = center.x;
						    result[at + 1] = center.y;
						    result[at + 2] = center.z;
						    result[at + 3] = radius;
						}
						""";
	}

	private record Particle(ParticleStyle style, Vector3f spawn, Vector3f velocity, float sizeScale, float spin,
							float tintScale, ParticleCollision.Contact contact, int look) {
	}

	private interface Expected {
		/** World position and normal of {@code vertex} at {@code age}, alive or not. */
		void vertex(Particle p, float age, float[] vertex, Vector3f position, Vector3f normal);

		/** Alpha the shader writes, given the colour alpha. */
		default float alpha(float colourAlpha) {
			return colourAlpha;
		}
	}

	private static float size(Particle p, float age) {
		return ParticleMotion.alive(age, p.contact.life())
				? ParticleMotion.size(p.style, p.sizeScale, ParticleMotion.unitAge(age, p.contact.life())) : 0.0f;
	}

	private static final Expected DECAL = (p, age, vertex, position, normal) -> {
		ParticleShapes.decalCorner(p.spawn, p.velocity, p.spin, vertex[0], vertex[1], size(p, age), position)
				.add(ORIGIN);
		normal.set(p.velocity)
				.normalize();
	};

	private static final Expected STREAK = (p, age, vertex, position, normal) -> {
		Vector3f at = ParticleCollision.positionAt(p.style, p.spawn, p.velocity, p.contact, age, new Vector3f())
				.add(ORIGIN);
		Vector3f v = ParticleCollision.motionAt(p.style, p.velocity, p.contact, age, new Vector3f());
		ParticleShapes.streakCorner(at, v, CAMERA, VIEW_INVERSE.getColumn(0, new Vector3f()), vertex[0], vertex[1],
				size(p, age), p.style.streak, position);
		normal.set(VIEW_INVERSE.getColumn(2, new Vector3f()))
				.negate();
	};

	private static final Expected BODY = new Expected() {
		@Override
		public void vertex(Particle p, float age, float[] vertex, Vector3f position, Vector3f normal) {
			Matrix3f basis = ParticleShapes.bodyBasis(p.style, p.velocity, p.spin, p.contact, age, new Matrix3f());
			float scale = size(p, age) * ParticleShapes.bodyScale(p.style,
					ParticleMotion.unitAge(age, p.contact.life()));
			ParticleCollision.positionAt(p.style, p.spawn, p.velocity, p.contact, age, position)
					.add(ORIGIN)
					.add(basis.transform(new Vector3f(vertex[0], vertex[1], vertex[2]).mul(scale)));
			basis.transform(normal.set(vertex[3], vertex[4], vertex[5]));
		}

		@Override
		public float alpha(float colourAlpha) {
			return 1.0f;
		}
	};

	private static void run(String type, float[][] mesh, Vector4f sphere, Expected expected, Particle p) {
		float[] data = new float[ParticleBuffer.STYLE_REGION_FLOATS + ParticleBuffer.PARTICLE_FLOATS];
		p.style.write(data, 0);
		int at = ParticleBuffer.STYLE_REGION_FLOATS;
		float[] record = {p.spawn.x, p.spawn.y, p.spawn.z, 0.0f, p.velocity.x, p.velocity.y, p.velocity.z,
				p.contact.life(), 0.0f, p.sizeScale, p.spin, p.tintScale, p.contact.contactAge(), p.contact.normal(),
				p.contact.restAge(), p.look};
		System.arraycopy(record, 0, data, at, record.length);

		float[] vertices = new float[MAX_VERTICES * 6];
		for (int v = 0; v < mesh.length; v++) {
			System.arraycopy(mesh[v], 0, vertices, v * 6, 6);
		}

		try (HeadlessGl gl = HeadlessGl.createOrSkip()) {
			int program = gl.computeProgram(harness(type));
			int texels = glGenBuffers();
			int texture = glGenTextures();
			int in = glGenBuffers();
			int out = glGenBuffers();
			try {
				glBindBuffer(GL_TEXTURE_BUFFER, texels);
				glBufferData(GL_TEXTURE_BUFFER, data, GL_STATIC_DRAW);
				glActiveTexture(GL_TEXTURE3);
				glBindTexture(GL_TEXTURE_BUFFER, texture);
				glTexBuffer(GL_TEXTURE_BUFFER, GL_RGBA32F, texels);

				glBindBuffer(GL_SHADER_STORAGE_BUFFER, in);
				glBufferData(GL_SHADER_STORAGE_BUFFER, vertices, GL_STATIC_DRAW);
				glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, in);

				int outputs = MAX_VERTICES * VERTEX_FLOATS + 4;
				glBindBuffer(GL_SHADER_STORAGE_BUFFER, out);
				glBufferData(GL_SHADER_STORAGE_BUFFER, new float[outputs], GL_STATIC_DRAW);
				glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, out);

				glUseProgram(program);
				HeadlessGl.samplerUnit(program, "_gemrender_particles", SAMPLER_UNIT);
				glUniform3f(1, CAMERA.x, CAMERA.y, CAMERA.z);
				glUniformMatrix4fv(2, false, VIEW_INVERSE.get(new float[16]));
				glUniform3f(3, ORIGIN.x, ORIGIN.y, ORIGIN.z);
				glUniform1i(4, mesh.length);
				glUniform4f(5, sphere.x, sphere.y, sphere.z, sphere.w);

				Vector3f position = new Vector3f();
				Vector3f normal = new Vector3f();
				int aliveChecked = 0;
				for (float age = 0.0f; age < p.contact.life() + 0.2f; age += 0.04f) {
					glUniform1f(0, age);
					glDispatchCompute(1, 1, 1);
					glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
					float[] actual = new float[outputs];
					glBindBuffer(GL_SHADER_STORAGE_BUFFER, out);
					glGetBufferSubData(GL_SHADER_STORAGE_BUFFER, 0, actual);

					String when = "%s at t=%.2f".formatted(type, age);
					float unitAge = ParticleMotion.unitAge(age, p.contact.life());
					float cool = ParticleMotion.cool(p.style, unitAge);
					float[] rgb = {p.style.tintRed * ParticleLook.red(p.look) / 255.0f,
							p.style.tintGreen * ParticleLook.green(p.look) / 255.0f,
							p.style.tintBlue * ParticleLook.blue(p.look) / 255.0f};
					boolean alive = ParticleMotion.alive(age, p.contact.life());

					for (int v = 0; v < mesh.length; v++) {
						int o = v * VERTEX_FLOATS;
						expected.vertex(p, age, mesh[v], position, normal);
						assertThat(new Vector3f(actual[o], actual[o + 1], actual[o + 2]).distance(position))
								.as("%s vertex %d position, expected %s", when, v, position)
								.isLessThan(2e-3f);
						assertThat(new Vector3f(actual[o + 3], actual[o + 4], actual[o + 5]).distance(normal))
								.as("%s vertex %d normal", when, v)
								.isLessThan(2e-3f);
						for (int c = 0; c < 3; c++) {
							assertThat(actual[o + 6 + c]).as("%s colour %d", when, c)
									.isCloseTo(Math.min(1.0f, rgb[c] * cool * p.tintScale), within(2e-2f));
						}
						assertThat(actual[o + 9]).as("%s alpha", when)
								.isCloseTo(expected.alpha(ParticleMotion.alpha(p.style, unitAge)), within(1e-4f));
						assertThat(actual[o + 10]).as("%s block light", when)
								.isCloseTo(ParticleLook.blockLight(p.look) / 16.0f, within(1e-6f));
						assertThat(actual[o + 11]).as("%s sky light", when)
								.isCloseTo(ParticleLook.skyLight(p.look) / 16.0f, within(1e-6f));
					}

					int c = MAX_VERTICES * VERTEX_FLOATS;
					Vector3f center = new Vector3f(actual[c], actual[c + 1], actual[c + 2]);
					float radius = actual[c + 3];
					if (!alive) {
						assertThat(radius).as("%s dead => culled", when).isNegative();
						continue;
					}
					aliveChecked++;
					for (int v = 0; v < mesh.length; v++) {
						int o = v * VERTEX_FLOATS;
						float reach = new Vector3f(actual[o], actual[o + 1], actual[o + 2]).distance(center);
						assertThat(reach).as("%s vertex %d inside cull sphere r=%s", when, v, radius)
								.isLessThanOrEqualTo(radius + 1e-5f);
					}
				}
				assertThat(aliveChecked).as("alive frames").isGreaterThan(10);
			} finally {
				glDeleteBuffers(out);
				glDeleteBuffers(in);
				glDeleteTextures(texture);
				glDeleteBuffers(texels);
				glDeleteProgram(program);
			}
		}
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

	private static ParticleCollision.Contact bounce(ParticleStyle style, Vector3f spawn, Vector3f velocity,
			float life, float radius) {
		ParticleCollision.Contact contact = ParticleCollision.predict(floorAt(0.0), style, spawn.x, spawn.y, spawn.z,
				velocity.x, velocity.y, velocity.z, life, radius);
		assertThat(contact.restAge()).as("fixture settles inside its life").isLessThan(life * 0.7f);
		return contact;
	}

	@Test
	@DisplayName("particle_decal: corners on the lifted plane, grown by size, normal out, lit; culled when dead")
	void decal() {
		ParticleStyle style = ParticleStyle.builder()
				.size(0.14f, 0.3f)
				.tint(0.9f, 0.85f, 0.8f)
				.fadeOut(0.6f)
				.build();
		Vector3f normal = new Vector3f(-0.6f, 0.0f, 0.8f);
		run("particle_decal", QUAD, QUAD_SPHERE, DECAL, new Particle(style, new Vector3f(1.5f, 2.25f, 3.0f), normal,
				0.5f, 2.1f, 1.0f, ParticleCollision.none(1.2f), ParticleLook.of(0xC0A080, 9, 13)));
	}

	@Test
	@DisplayName("particle_streak: stretched along motion in flight, a dot once settled; cull holds the tail")
	void streak() {
		ParticleStyle style = ParticleStyle.builder()
				.drag(ParticleStyle.dragFromPerTickFactor(0.97f))
				.gravity(18.0f)
				.size(0.035f, 0.0f)
				.tint(0xFFB040)
				.cool(0.35f, 0.7f)
				.streak(0.05f)
				.bouncesOnContact(0.45f, 0.6f)
				.build();
		Vector3f spawn = new Vector3f(0.5f, 1.6f, -0.25f);
		Vector3f velocity = new Vector3f(6.0f, 4.0f, -3.0f);
		run("particle_streak", QUAD, QUAD_SPHERE, STREAK, new Particle(style, spawn, velocity, 1.0f, 0.0f, 1.0f,
				bounce(style, spawn, velocity, 2.0f, 0.0f), ParticleLook.of(0xFFFFFF)));
	}

	@Test
	@DisplayName("particle_body: tumbles, lands, shrinks through the fade window, opaque; cull holds every vertex")
	void body() {
		ParticleStyle style = ParticleStyle.builder()
				.gravity(16.0f)
				.spin(28.0f)
				.size(1.2f, 0.0f)
				.bouncesOnContact(0.3f, 0.45f)
				.fadeOut(0.75f)
				.build();
		Vector3f spawn = new Vector3f(-0.8f, 1.3f, 0.5f);
		Vector3f velocity = new Vector3f(1.1f, 3.0f, 0.4f);
		run("particle_body", BOX, BOX_SPHERE, BODY, new Particle(style, spawn, velocity, 1.0f, 0.9f, 1.0f,
				bounce(style, spawn, velocity, 3.0f, 0.02f), ParticleLook.of(0xE0B050, 12, 7)));
	}
}
