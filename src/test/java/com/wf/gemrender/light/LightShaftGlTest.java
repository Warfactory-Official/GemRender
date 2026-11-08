package com.wf.gemrender.light;

import static org.assertj.core.api.Assertions.assertThat;
import static org.lwjgl.opengl.GL46C.*;

import com.wf.gemrender.gl.HeadlessGl;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.Random;

/**
 * {@code gemrender_shaft} against a fine numeric integral of the same medium: the analytic cone clip, the scene-depth
 * clamp, the falloff and phase terms; and, with a traced 1-block wall between the camera side and the far side, that
 * the shaft stops at the wall.
 */
@Tag("gl")
class LightShaftGlTest {
	private static final Vec3 EYE = new Vec3(-1234.7, 70.2, -987.3);
	private static final float DENSITY = 0.01f, PHASE_G = 0.45f;
	private static final int RAYS = 2048;

	private static LightFrame.Light spot(double x, double y, double z, Vector3f dir, float range, float inner,
			float outer, int rgb, float intensity) {
		dir.normalize();
		Vector3f up = new Vector3f(0, 1, 0).sub(new Vector3f(dir).mul(dir.y)).normalize();
		float cosOuter = (float) Math.cos(Math.toRadians(outer));
		float cosInner = (float) Math.cos(Math.toRadians(inner));
		LightFrame.Light light = new LightFrame.Light();
		light.set(x, y, z, range, rgb, intensity, true, dir.x, dir.y, dir.z, up.x, up.y, up.z, cosOuter,
				1.0f / (cosInner - cosOuter), -1, false);
		light.relativeTo(EYE);
		return light;
	}

	private static String source() {
		return "#version 460 core\nlayout(local_size_x = 64) in;\n"
				+ LightShaders.include().replace("dFdx(_grl_p)", "vec3(0.0)").replace("dFdy(_grl_p)", "vec3(0.0)")
				+ shader("include/shaft.glsl") + """
				uniform int _slot;
				layout(std430, binding = 0) readonly buffer In { vec4 rays[]; };
				layout(std430, binding = 1) writeonly buffer Out { vec4 result[]; };
				void main() {
				    uint i = gl_GlobalInvocationID.x;
				    result[i] = vec4(gemrender_shaft(_slot, rays[i].xyz, rays[i].w, 0.5), 0.0);
				}
				""";
	}

	private static String shader(String name) {
		try (var in = LightShaftGlTest.class.getResourceAsStream("/assets/gemrender/shaders/" + name)) {
			return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8) + "\n";
		} catch (java.io.IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
	}

	private static ByteBuffer ubo(LightFrame.Light light) {
		int cx = (int) Math.floor(EYE.x / 16.0), cy = (int) Math.floor(EYE.y / 16.0), cz = (int) Math.floor(EYE.z / 16.0);
		ByteBuffer ubo = MemoryUtil.memCalloc(LightFrame.UBO_BYTES);
		ubo.putFloat((float) (EYE.x - cx * 16.0)).putFloat((float) (EYE.y - cy * 16.0))
				.putFloat((float) (EYE.z - cz * 16.0)).putFloat(0);
		ubo.putInt(cx).putInt(cy).putInt(cz).putInt(1);
		ubo.position(LightFrame.HEADER_BYTES);
		light.write(ubo);
		ubo.position(LightFrame.UBO_BYTES).flip();
		return ubo;
	}

	/** GPU: one result per ray (rgb). Light is slot 0; UBO bound by the caller. */
	private static float[] march(HeadlessGl gl, float[][] rays) {
		FloatBuffer in = MemoryUtil.memAllocFloat(rays.length * 4);
		for (float[] r : rays) in.put(r);
		in.flip();
		int program = gl.computeProgram(source());
		glUniformBlockBinding(program, glGetUniformBlockIndex(program, "GemRenderLights"), LightFrame.UBO_BINDING);
		int[] buffers = new int[2];
		glGenBuffers(buffers);
		glBindBuffer(GL_SHADER_STORAGE_BUFFER, buffers[0]);
		glBufferData(GL_SHADER_STORAGE_BUFFER, in, GL_STATIC_DRAW);
		glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, buffers[0]);
		glBindBuffer(GL_SHADER_STORAGE_BUFFER, buffers[1]);
		glBufferData(GL_SHADER_STORAGE_BUFFER, (long) rays.length * 16, GL_STATIC_DRAW);
		glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, buffers[1]);
		glUseProgram(program);
		HeadlessGl.samplerUnit(program, "_grl_grid", LightGrid.UNIT);
		HeadlessGl.samplerUnit(program, "_grl_cookies", LightCookies.UNIT);
		HeadlessGl.samplerUnit(program, "_grl_shadows", LightShadows.UNIT);
		glUniform1i(glGetUniformLocation(program, "_slot"), 0);
		glUniform1f(glGetUniformLocation(program, "_gs_density"), DENSITY);
		glUniform1f(glGetUniformLocation(program, "_gs_phaseG"), PHASE_G);
		glUniform1f(glGetUniformLocation(program, "_gs_dust"), 0.0f);
		glUniform1f(glGetUniformLocation(program, "_gs_time"), 0.0f);
		glUniform1i(glGetUniformLocation(program, "_gs_steps"), 64);
		glDispatchCompute((rays.length + 63) / 64, 1, 1);
		glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
		float[] out = new float[rays.length * 4];
		glGetBufferSubData(GL_SHADER_STORAGE_BUFFER, 0, out);
		glDeleteProgram(program);
		glDeleteBuffers(buffers);
		MemoryUtil.memFree(in);
		return out;
	}

	/**
	 * The shader's medium, 20000 midpoint samples; {@code xLimit}: only points with x in [lo, hi) count (slab
	 * bookkeeping for the wall test; infinities for none).
	 */
	private static double reference(LightFrame.Light l, float[] ray, double lo, double hi) {
		double tMax = Math.min(ray[3], l.range + Math.sqrt(l.rx * l.rx + l.ry * l.ry + l.rz * l.rz));
		int n = 20000;
		double dt = tMax / n, sum = 0.0;
		double soft2 = 1.0, invR2 = 1.0 / ((double) l.range * l.range);
		for (int i = 0; i < n; i++) {
			double t = (i + 0.5) * dt;
			double px = ray[0] * t, py = ray[1] * t, pz = ray[2] * t;
			if (px < lo || px >= hi) continue;
			double lx = px - l.rx, ly = py - l.ry, lz = pz - l.rz;
			double r2 = lx * lx + ly * ly + lz * lz, r = Math.sqrt(r2);
			if (r2 >= (double) l.range * l.range) continue;
			double axial = (lx * l.dx + ly * l.dy + lz * l.dz) / r;
			double cone = Math.min(1.0, Math.max(0.0, (axial - l.cosOuter) * l.spotScale));
			if (cone <= 0.0) continue;
			double x = r2 * invR2, window = 1.0 - x * x;
			double e = cone * cone * window * window / (r2 + soft2);
			double c = -(lx * ray[0] + ly * ray[1] + lz * ray[2]) / r;
			double g = PHASE_G, gg = g * g, d = Math.max(1.0 + gg - 2.0 * g * c, 1.0e-3);
			sum += e * (1.0 - gg) / (4.0 * Math.PI * d * Math.sqrt(d));
		}
		return sum * dt * DENSITY;
	}

	private static float[] ray(Random random, LightFrame.Light l, double aimAtConeFraction) {
		Vector3f dir;
		if (random.nextDouble() < aimAtConeFraction) {
			// A point inside the cone, then a ray from the camera through it.
			double r = (0.05 + 0.95 * random.nextDouble()) * l.range;
			double phi = random.nextDouble() * 2 * Math.PI;
			double spread = Math.tan(Math.acos(l.cosOuter)) * r * Math.sqrt(random.nextDouble());
			Vector3f axis = new Vector3f(l.dx, l.dy, l.dz), up = new Vector3f(l.ux, l.uy, l.uz);
			Vector3f side = new Vector3f(axis).cross(up);
			Vector3f p = new Vector3f(l.rx, l.ry, l.rz).fma((float) r, axis)
					.fma((float) (Math.cos(phi) * spread), up).fma((float) (Math.sin(phi) * spread), side);
			dir = p.normalize();
		} else {
			dir = new Vector3f(random.nextFloat() * 2 - 1, random.nextFloat() * 2 - 1, random.nextFloat() * 2 - 1).normalize();
		}
		float tScene = random.nextInt(3) == 0 ? 1.0e4f : 4.0f + random.nextFloat() * 40.0f;
		return new float[] {dir.x, dir.y, dir.z, tScene};
	}

	@Test
	void shaftMatchesTheNumericIntegral() {
		try (HeadlessGl gl = HeadlessGl.createOrSkip()) {
			LightFrame.Light l = spot(EYE.x + 6.0, EYE.y + 1.0, EYE.z - 3.0, new Vector3f(0.3f, -0.1f, 1.0f), 30.0f,
					6.0f, 25.0f, 0xFFE0C0, 60.0f);
			ByteBuffer ubo = ubo(l);
			int buffer = glGenBuffers();
			glBindBuffer(GL_UNIFORM_BUFFER, buffer);
			glBufferData(GL_UNIFORM_BUFFER, ubo, GL_STATIC_DRAW);
			glBindBufferBase(GL_UNIFORM_BUFFER, LightFrame.UBO_BINDING, buffer);

			Random random = new Random(5);
			float[][] rays = new float[RAYS][];
			for (int i = 0; i < RAYS; i++) rays[i] = ray(random, l, 0.7);
			float[] out = march(gl, rays);

			int hits = 0, misses = 0;
			double worst = 0.0, total = 0.0;
			for (int i = 0; i < RAYS; i++) {
				double expected = reference(l, rays[i], Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY) * l.r;
				double got = out[i * 4];
				if (expected < 1.0e-5) {
					misses++;
					assertThat(got).as("ray %d misses the cone", i).isLessThan(2.0e-4);
					continue;
				}
				hits++;
				double error = Math.abs(got - expected) / expected;
				worst = Math.max(worst, error);
				total += error;
			}
			System.out.println("[gemrender] shaft rays hit " + hits + ", missed " + misses + ", mean error "
					+ total / Math.max(hits, 1) + ", worst " + worst);
			assertThat(hits).as("rays through the cone (non-vacuous)").isGreaterThan(RAYS / 3);
			assertThat(misses).as("rays missing it (non-vacuous)").isGreaterThan(20);
			assertThat(total / hits).as("mean relative error").isLessThan(0.03);
			assertThat(worst).as("worst relative error").isLessThan(0.20);

			glDeleteBuffers(buffer);
			MemoryUtil.memFree(ubo);
		}
	}

	/** A 1-block wall at x = EYE.x - 3, light on the camera's side shining through it: nothing past the wall is lit. */
	@Test
	void shaftStopsAtAWall() {
		try (HeadlessGl gl = HeadlessGl.createOrSkip()) {
			int wallX = (int) Math.floor(EYE.x) - 3;
			int[] cam = {(int) Math.floor(EYE.x / 16.0), (int) Math.floor(EYE.y / 16.0), (int) Math.floor(EYE.z / 16.0)};
			LightOccupancy occupancy = new LightOccupancy();
			occupancy.claimWindow(cam[0], cam[1], cam[2]);
			for (int y = (int) EYE.y - 40; y < (int) EYE.y + 40; y++) {
				for (int z = (int) EYE.z - 40; z < (int) EYE.z + 40; z++) occupancy.set(wallX, y, z, true);
			}
			occupancy.upload();

			LightFrame.Light l = spot(wallX + 6.5, EYE.y + 1.0, EYE.z + 2.0, new Vector3f(-1.0f, -0.1f, 0.15f), 24.0f,
					12.0f, 40.0f, 0xFFFFFF, 80.0f);
			LightShadows shadows = new LightShadows();
			shadows.begin();
			l.shadow = true;
			l.tile = shadows.claim(0);
			ByteBuffer ubo = ubo(l);
			int buffer = glGenBuffers();
			glBindBuffer(GL_UNIFORM_BUFFER, buffer);
			glBufferData(GL_UNIFORM_BUFFER, ubo, GL_STATIC_DRAW);
			glBindBufferBase(GL_UNIFORM_BUFFER, LightFrame.UBO_BINDING, buffer);
			shadows.drawUnmanaged();
			shadows.bind();
			glBindFramebuffer(GL_FRAMEBUFFER, 0);

			double wallRel = wallX - EYE.x;
			Random random = new Random(9);
			float[][] rays = new float[RAYS][];
			for (int i = 0; i < RAYS; i++) rays[i] = ray(random, l, 0.9);
			float[] out = march(gl, rays);

			int through = 0;
			for (int i = 0; i < RAYS; i++) {
				double front = reference(l, rays[i], wallRel + 1.5, Double.POSITIVE_INFINITY) * l.r;
				double slab = reference(l, rays[i], wallRel - 0.5, wallRel + 1.5) * l.r;
				double behind = reference(l, rays[i], Double.NEGATIVE_INFINITY, wallRel - 0.5) * l.r;
				double got = out[i * 4];
				assertThat(got).as("ray %d: front %.5f slab %.5f behind %.5f", i, front, slab, behind)
						.isBetween(front * 0.85 - 2.0e-4, front * 1.15 + slab + 2.0e-4);
				if (behind > 0.3 * (front + slab + behind) && behind > 1.0e-3) through++;
			}
			System.out.println("[gemrender] shaft rays with most of their light behind the wall: " + through);
			assertThat(through).as("rays that would cross the wall (non-vacuous)").isGreaterThan(30);

			glDeleteBuffers(buffer);
			MemoryUtil.memFree(ubo);
		}
	}

	/**
	 * The real programs ({@code light_trace.vert} fullscreen triangle + {@code shaft.frag}) into a 64 x 64 target: view
	 * rays rebuilt from the projection, clamped by a scene-depth texture, tone mapped, cut by the scissor box.
	 */
	@Test
	void theFragmentProgramRendersTheShaft() {
		try (HeadlessGl gl = HeadlessGl.createOrSkip()) {
			final int size = 64;
			final float tanHalf = (float) Math.tan(Math.toRadians(35.0));
			// Camera at the origin looking down -z; a lamp 1 m ahead and 0.5 m up, shining on down -z.
			LightFrame.Light l = spot(EYE.x, EYE.y + 0.5, EYE.z - 1.0, new Vector3f(0, 0, -1), 20.0f, 10.0f, 30.0f,
					0xFFFFFF, 40.0f);
			ByteBuffer ubo = ubo(l);
			int buffer = glGenBuffers();
			glBindBuffer(GL_UNIFORM_BUFFER, buffer);
			glBufferData(GL_UNIFORM_BUFFER, ubo, GL_STATIC_DRAW);
			glBindBufferBase(GL_UNIFORM_BUFFER, LightFrame.UBO_BINDING, buffer);

			int program = com.wf.gemrender.render.GlPrograms.link("shaft test",
					"#version 150\n" + LightShadows.source("light_trace.vert"),
					"#version 150\n" + LightShaders.include() + LightShadows.source("include/shaft.glsl")
							+ LightShadows.source("shaft.frag"));
			glUniformBlockBinding(program, glGetUniformBlockIndex(program, "GemRenderLights"), LightFrame.UBO_BINDING);
			glUseProgram(program);
			HeadlessGl.samplerUnit(program, "_grl_grid", LightGrid.UNIT);
			HeadlessGl.samplerUnit(program, "_grl_cookies", LightCookies.UNIT);
			HeadlessGl.samplerUnit(program, "_grl_shadows", LightShadows.UNIT);
			HeadlessGl.samplerUnit(program, "_gs_scene", 0);
			glUniform1i(glGetUniformLocation(program, "_gs_slot"), 0);
			glUniform1i(glGetUniformLocation(program, "_gs_steps"), 32);
			glUniform1f(glGetUniformLocation(program, "_gs_phaseG"), PHASE_G);
			glUniform1f(glGetUniformLocation(program, "_gs_density"), DENSITY * 40.0f);
			glUniform1f(glGetUniformLocation(program, "_gs_dust"), 0.0f);
			glUniform1f(glGetUniformLocation(program, "_gs_time"), 0.0f);
			glUniform1f(glGetUniformLocation(program, "_gs_dayFade"), 1.0f);
			glUniform2f(glGetUniformLocation(program, "_gs_tanHalf"), tanHalf, tanHalf);
			glUniform2f(glGetUniformLocation(program, "_gs_size"), size, size);
			glUniformMatrix3fv(glGetUniformLocation(program, "_gs_viewInverse"), false,
					new float[] {1, 0, 0, 0, 1, 0, 0, 0, 1});

			int color = glGenTextures(), depth = glGenTextures(), fbo = glGenFramebuffers(), vao = glGenVertexArrays();
			glBindTexture(GL_TEXTURE_2D, color);
			glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, size, size, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
			glBindFramebuffer(GL_FRAMEBUFFER, fbo);
			glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, color, 0);
			glViewport(0, 0, size, size);
			glBindVertexArray(vao);
			glActiveTexture(GL_TEXTURE0);
			glBindTexture(GL_TEXTURE_2D, depth);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

			// 1: far geometry; full target. 2: geometry 0.5 m ahead (in front of the lamp). 3: far, right half only.
			float[] viewZ = {1000.0f, 0.5f, 1000.0f};
			float[][] centre = new float[3][];
			float[][] corner = new float[3][];
			float[][] left = new float[3][];
			for (int pass = 0; pass < 3; pass++) {
				FloatBuffer z = MemoryUtil.memAllocFloat(size * size);
				for (int i = 0; i < size * size; i++) z.put(viewZ[pass]);
				z.flip();
				glTexImage2D(GL_TEXTURE_2D, 0, GL_R32F, size, size, 0, GL_RED, GL_FLOAT, z);
				MemoryUtil.memFree(z);
				glClearColor(0, 0, 0, 0);
				glClear(GL_COLOR_BUFFER_BIT);
				if (pass == 2) {
					glEnable(GL_SCISSOR_TEST);
					glScissor(size / 2, 0, size / 2, size);
				} else {
					glDisable(GL_SCISSOR_TEST);
				}
				glDrawArrays(GL_TRIANGLES, 0, 3);
				glDisable(GL_SCISSOR_TEST);
				ByteBuffer px = MemoryUtil.memAlloc(size * size * 4);
				glReadPixels(0, 0, size, size, GL_RGBA, GL_UNSIGNED_BYTE, px);
				centre[pass] = pixel(px, size / 2 + 4, size / 2, size);
				corner[pass] = pixel(px, 2, size - 3, size);
				left[pass] = pixel(px, size / 4, size / 2, size);
				if (pass == 0) {
					// The lit pixel against the numeric integral through the same ray, tone mapped.
					int x = size / 2 + 4, y = size / 2;
					float nx = (x + 0.5f) / size * 2 - 1, ny = (y + 0.5f) / size * 2 - 1;
					Vector3f d = new Vector3f(nx * tanHalf, ny * tanHalf, -1).normalize();
					double s = reference(l, new float[] {d.x, d.y, d.z, 1.0e4f}, Double.NEGATIVE_INFINITY,
							Double.POSITIVE_INFINITY) * l.r * 40.0;
					double expected = 1.0 - Math.exp(-s);
					assertThat((double) centre[0][0]).as("lit pixel vs integral (expected %.4f)", expected)
							.isCloseTo(expected, org.assertj.core.data.Offset.offset(0.03 + 0.1 * expected));
				}
				MemoryUtil.memFree(px);
			}
			System.out.println("[gemrender] shaft pixels: centre " + java.util.Arrays.toString(centre[0]) + ", corner "
					+ java.util.Arrays.toString(corner[0]) + ", behind-geometry " + java.util.Arrays.toString(centre[1])
					+ ", scissored-out " + java.util.Arrays.toString(left[2]));
			assertThat(centre[0][0]).as("beam through the middle").isGreaterThan(0.05f);
			assertThat(corner[0][0]).as("a ray missing the cone").isEqualTo(0.0f);
			assertThat(centre[1][0]).as("geometry in front of the lamp stops the ray").isEqualTo(0.0f);
			assertThat(left[2][0]).as("outside the scissor box").isEqualTo(0.0f);
			assertThat(centre[2][0]).as("inside the scissor box, same as unscissored").isEqualTo(centre[0][0]);

			glBindFramebuffer(GL_FRAMEBUFFER, 0);
			glDeleteFramebuffers(fbo);
			glDeleteTextures(new int[] {color, depth});
			glDeleteVertexArrays(vao);
			glDeleteProgram(program);
			glDeleteBuffers(buffer);
			MemoryUtil.memFree(ubo);
		}
	}

	private static float[] pixel(ByteBuffer px, int x, int y, int size) {
		int o = (y * size + x) * 4;
		return new float[] {(px.get(o) & 255) / 255.0f, (px.get(o + 1) & 255) / 255.0f, (px.get(o + 2) & 255) / 255.0f};
	}
}
