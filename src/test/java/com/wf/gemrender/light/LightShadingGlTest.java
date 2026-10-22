package com.wf.gemrender.light;

import static org.assertj.core.api.Assertions.assertThat;
import static org.lwjgl.opengl.GL46C.*;

import com.wf.gemrender.gl.HeadlessGl;
import com.wf.gemrender.gl.ShaderSources;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code gemrender_lights} through the real section-mask grid vs an all-lights Java reference: a grid cell
 * missing a contributing light (cone/sphere cull not conservative, wrap/indexing wrong) is a mismatch.
 * Shadows: real occupancy + trace pass, 1-block wall => far side exactly 0, near side = reference.
 * Plus every patched vanilla program compiles and links.
 */
@Tag("gl")
class LightShadingGlTest {
	private static final Vec3 EYE = new Vec3(-1234.7, 70.2, -987.3);

	private static final int POINTS = 4096;

	private static LightFrame.Light point(double x, double y, double z, float range, int rgb, float intensity) {
		LightFrame.Light light = new LightFrame.Light();
		light.set(x, y, z, range, rgb, intensity, false, 0, 0, 0, 0, 0, 0, -2.0f, 1.0f, -1, false);
		return light;
	}

	private static LightFrame.Light spot(double x, double y, double z, Vector3f dir, float range, float inner,
										 float outer, int rgb, float intensity) {
		dir.normalize();
		Vector3f up = new Vector3f(0, 1, 0).sub(new Vector3f(dir).mul(dir.y)).normalize();
		float cosOuter = (float) Math.cos(Math.toRadians(outer));
		float cosInner = (float) Math.cos(Math.toRadians(inner));
		LightFrame.Light light = new LightFrame.Light();
		light.set(x, y, z, range, rgb, intensity, true, dir.x, dir.y, dir.z, up.x, up.y, up.z, cosOuter,
				1.0f / (cosInner - cosOuter), -1, false);
		return light;
	}

	private static List<LightFrame.Light> lights() {
		List<LightFrame.Light> lights = new ArrayList<>();
		lights.add(point(EYE.x + 10.3, EYE.y - 2.1, EYE.z + 7.7, 14.0f, 0xFFFFFF, 20.0f));
		lights.add(spot(EYE.x - 5.0, EYE.y + 3.0, EYE.z - 6.0, new Vector3f(1.0f, -0.4f, 0.8f), 25.0f, 10.0f, 35.0f,
				0xFF8040, 30.0f));
		lights.add(spot(EYE.x + 0.5, EYE.y + 0.2, EYE.z + 0.5, new Vector3f(0.0f, 0.1f, 1.0f), 9.0f, 30.0f, 80.0f,
				0x40C0FF, 12.0f));
		lights.add(spot(EYE.x - 20.0, EYE.y + 12.0, EYE.z + 18.0, new Vector3f(0.3f, -1.0f, -0.2f), 40.0f, 3.0f, 8.0f,
				0xFFFFFF, 200.0f));
		for (LightFrame.Light light : lights) {
			light.relativeTo(EYE);
		}
		return lights;
	}

	/** The shader's per-light term in doubles. */
	private static double[] reference(List<LightFrame.Light> lights, float[] p, float[] n) {
		double[] sum = new double[3];
		for (LightFrame.Light light : lights) {
			double lx = light.rx - p[0], ly = light.ry - p[1], lz = light.rz - p[2];
			double d2 = lx * lx + ly * ly + lz * lz;
			double r2 = (double) light.range * light.range;
			if (d2 >= r2) {
				continue;
			}
			double dist = Math.sqrt(d2);
			lx /= dist;
			ly /= dist;
			lz /= dist;
			double x = d2 / r2;
			double w = 1.0 - x * x;
			double e = w * w / (d2 + 1.0);
			if (n[0] * n[0] + n[1] * n[1] + n[2] * n[2] > 0.25) {
				e *= Math.max(n[0] * lx + n[1] * ly + n[2] * lz, 0.0);
			}
			if (light.spot) {
				double axial = -(lx * light.dx + ly * light.dy + lz * light.dz);
				double cone = Math.min(1.0, Math.max(0.0, (axial - light.cosOuter) * light.spotScale));
				e *= cone * cone;
			}
			sum[0] += light.r * e;
			sum[1] += light.g * e;
			sum[2] += light.b * e;
		}
		return sum;
	}

	private static String computeSource() {
		String include = LightShaders.include()
				.replace("dFdx(_grl_p)", "vec3(0.0)")
				.replace("dFdy(_grl_p)", "vec3(0.0)");
		return "#version 460 core\nlayout(local_size_x = 64) in;\n" + include + """
				layout(std430, binding = 0) readonly buffer In { vec4 samples[]; };
				layout(std430, binding = 1) writeonly buffer Out { vec4 result[]; };
				void main() {
				    uint i = gl_GlobalInvocationID.x;
				    vec3 p = samples[i * 2u].xyz;
				    vec3 n = samples[i * 2u + 1u].xyz;
				    gemrender_lightPrepare(p);
				    result[i * 2u] = vec4(gemrender_lights(p, n), 0.0);
				    result[i * 2u + 1u] = vec4(_grl_irSum, 0.0);
				}
				""";
	}

	private static int[] camSection() {
		return new int[] {(int) Math.floor(EYE.x / 16.0), (int) Math.floor(EYE.y / 16.0), (int) Math.floor(EYE.z / 16.0)};
	}

	private static ByteBuffer frame(List<LightFrame.Light> lights) {
		return frame(lights, Lights.IrView.OFF, 0.0f);
	}

	private static ByteBuffer frame(List<LightFrame.Light> lights, Lights.IrView irView) {
		return frame(lights, irView, 0.0f);
	}

	/** UBO + section grid for {@code lights} (indices = list order); returns the UBO bytes. */
	private static ByteBuffer frame(List<LightFrame.Light> lights, Lights.IrView irView, float night) {
		int[] cam = camSection();
		LightGrid grid = new LightGrid();
		grid.begin();
		ByteBuffer ubo = MemoryUtil.memCalloc(LightFrame.UBO_BYTES);
		ubo.putFloat((float) (EYE.x - cam[0] * 16.0)).putFloat((float) (EYE.y - cam[1] * 16.0))
				.putFloat((float) (EYE.z - cam[2] * 16.0)).putFloat(irView.ordinal());
		ubo.putInt(cam[0]).putInt(cam[1]).putInt(cam[2]).putInt(lights.size());
		ubo.putFloat(night).putFloat(0.0f).putFloat(0.0f).putFloat(0.0f);
		ubo.position(LightFrame.HEADER_BYTES);
		for (int i = 0; i < lights.size(); i++) {
			lights.get(i).write(ubo);
			grid.add(i, lights.get(i), cam[0], cam[1], cam[2], EYE.x, EYE.y, EYE.z);
		}
		ubo.position(LightFrame.UBO_BYTES).flip();
		grid.upload();
		return ubo;
	}

	private static void bindUbo(int buffer, ByteBuffer ubo) {
		glBindBuffer(GL_UNIFORM_BUFFER, buffer);
		glBufferData(GL_UNIFORM_BUFFER, ubo, GL_STATIC_DRAW);
		glBindBufferBase(GL_UNIFORM_BUFFER, LightFrame.UBO_BINDING, buffer);
	}

	/** Per sample on the GPU, 8 floats: {@code gemrender_lights(p, n)}, {@code _grl_irSum}; UBO already bound. */
	private static float[] shade(HeadlessGl gl, float[][] ps, float[][] ns) {
		int count = ps.length;
		FloatBuffer samples = MemoryUtil.memAllocFloat(count * 8);
		for (int i = 0; i < count; i++) {
			samples.put(ps[i]).put(0.0f).put(ns[i]).put(0.0f);
		}
		samples.flip();
		int program = gl.computeProgram(computeSource());
		glUniformBlockBinding(program, glGetUniformBlockIndex(program, "GemRenderLights"), LightFrame.UBO_BINDING);
		int[] buffers = new int[2];
		glGenBuffers(buffers);
		glBindBuffer(GL_SHADER_STORAGE_BUFFER, buffers[0]);
		glBufferData(GL_SHADER_STORAGE_BUFFER, samples, GL_STATIC_DRAW);
		glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, buffers[0]);
		glBindBuffer(GL_SHADER_STORAGE_BUFFER, buffers[1]);
		glBufferData(GL_SHADER_STORAGE_BUFFER, (long) count * 32, GL_STATIC_DRAW);
		glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, buffers[1]);

		glUseProgram(program);
		HeadlessGl.samplerUnit(program, "_grl_grid", LightGrid.UNIT);
		HeadlessGl.samplerUnit(program, "_grl_cookies", LightCookies.UNIT);
		HeadlessGl.samplerUnit(program, "_grl_shadows", LightShadows.UNIT);
		glDispatchCompute((count + 63) / 64, 1, 1);
		glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);

		float[] out = new float[count * 8];
		glGetBufferSubData(GL_SHADER_STORAGE_BUFFER, 0, out);
		glDeleteProgram(program);
		glDeleteBuffers(buffers);
		MemoryUtil.memFree(samples);
		return out;
	}

	@Test
	void gridKeepsEveryContributingLight() {
		try (HeadlessGl gl = HeadlessGl.createOrSkip()) {
			List<LightFrame.Light> lights = lights();
			ByteBuffer ubo = frame(lights);

			Random random = new Random(7);
			float[][] ps = new float[POINTS][];
			float[][] ns = new float[POINTS][];
			for (int i = 0; i < POINTS; i++) {
				LightFrame.Light near = lights.get(i % lights.size());
				float spread = near.range * 1.1f;
				ps[i] = new float[] {near.rx + (random.nextFloat() * 2 - 1) * spread,
						near.ry + (random.nextFloat() * 2 - 1) * spread, near.rz + (random.nextFloat() * 2 - 1) * spread};
				Vector3f n = new Vector3f(random.nextFloat() * 2 - 1, random.nextFloat() * 2 - 1,
						random.nextFloat() * 2 - 1).normalize();
				ns[i] = i % 5 == 0 ? new float[3] : new float[] {n.x, n.y, n.z};
			}

			int buffer = glGenBuffers();
			bindUbo(buffer, ubo);
			float[] out = shade(gl, ps, ns);

			int lit = 0;
			for (int i = 0; i < POINTS; i++) {
				double[] expected = reference(lights, ps[i], ns[i]);
				for (int c = 0; c < 3; c++) {
					assertThat((double) out[i * 8 + c])
							.as("sample %d channel %d at %s", i, c, java.util.Arrays.toString(ps[i]))
							.isCloseTo(expected[c], org.assertj.core.data.Offset.offset(1.0e-4 + 1.0e-3 * expected[c]));
				}
				if (expected[0] + expected[1] + expected[2] > 1.0e-3) {
					lit++;
				}
			}
			System.out.println("[gemrender] light samples lit: " + lit + " / " + POINTS);
			assertThat(lit).as("samples lit by some light").isGreaterThan(POINTS / 8);

			glDeleteBuffers(buffer);
			MemoryUtil.memFree(ubo);
		}
	}

	/**
	 * Solid plane {@code x = WALL_X} (1 block thick, absolute) spanning the window; a point and a spot on the
	 * {@code +x} side. Far side ({@code x < WALL_X}) exactly 0; near side (>= 1.5 blocks off the wall) = reference.
	 */
	@Test
	void wallStopsShadowedLight() {
		try (HeadlessGl gl = HeadlessGl.createOrSkip()) {
			int[] cam = camSection();
			int wallX = (int) Math.floor(EYE.x) - 3;
			LightOccupancy occupancy = new LightOccupancy();
			occupancy.claimWindow(cam[0], cam[1], cam[2]);
			for (int y = (int) EYE.y - 40; y < (int) EYE.y + 40; y++) {
				for (int z = (int) EYE.z - 40; z < (int) EYE.z + 40; z++) {
					occupancy.set(wallX, y, z, true);
				}
			}
			occupancy.upload();

			List<LightFrame.Light> lights = new ArrayList<>();
			lights.add(point(wallX + 4.3, EYE.y + 0.7, EYE.z - 1.2, 14.0f, 0xFFFFFF, 20.0f));
			lights.add(spot(wallX + 6.5, EYE.y + 2.0, EYE.z + 3.0, new Vector3f(-1.0f, -0.2f, 0.3f), 20.0f, 15.0f,
					50.0f, 0xFF8040, 40.0f));
			LightShadows shadows = new LightShadows();
			shadows.begin();
			for (int i = 0; i < lights.size(); i++) {
				LightFrame.Light light = lights.get(i);
				light.shadow = true;
				light.tile = shadows.claim(i);
				light.relativeTo(EYE);
			}
			ByteBuffer ubo = frame(lights);
			int buffer = glGenBuffers();
			bindUbo(buffer, ubo);
			shadows.drawUnmanaged();
			shadows.bind();
			glBindFramebuffer(GL_FRAMEBUFFER, 0);

			float wallRel = (float) (wallX - EYE.x);
			Random random = new Random(11);
			float[][] ps = new float[POINTS][];
			float[][] ns = new float[POINTS][];
			for (int i = 0; i < POINTS; i++) {
				LightFrame.Light near = lights.get(i % lights.size());
				float x;
				do {
					x = near.rx + (random.nextFloat() * 2 - 1) * near.range;
				} while (x >= wallRel - 0.01f && x < wallRel + 2.5f);
				ps[i] = new float[] {x, near.ry + (random.nextFloat() * 2 - 1) * near.range,
						near.rz + (random.nextFloat() * 2 - 1) * near.range};
				Vector3f n = new Vector3f(random.nextFloat() * 2 - 1, random.nextFloat() * 2 - 1,
						random.nextFloat() * 2 - 1).normalize();
				ns[i] = i % 5 == 0 ? new float[3] : new float[] {n.x, n.y, n.z};
			}
			float[] out = shade(gl, ps, ns);

			int far = 0;
			int nearLit = 0;
			for (int i = 0; i < POINTS; i++) {
				double[] expected = reference(lights, ps[i], ns[i]);
				boolean behind = ps[i][0] < wallRel;
				for (int c = 0; c < 3; c++) {
					assertThat((double) out[i * 8 + c])
							.as("%s sample %d channel %d at %s", behind ? "far" : "near", i, c,
									java.util.Arrays.toString(ps[i]))
							.isCloseTo(behind ? 0.0 : expected[c],
									org.assertj.core.data.Offset.offset(behind ? 0.0 : 1.0e-4 + 1.0e-3 * expected[c]));
				}
				boolean lit = expected[0] + expected[1] + expected[2] > 1.0e-3;
				if (behind && lit) {
					far++;
				} else if (lit) {
					nearLit++;
				}
			}
			System.out.println("[gemrender] wall: far samples unshadowed-lit " + far + ", near lit " + nearLit);
			assertThat(far).as("far samples the reference lights (non-vacuous)").isGreaterThan(POINTS / 16);
			assertThat(nearLit).as("near samples lit").isGreaterThan(POINTS / 16);

			glDeleteBuffers(buffer);
			MemoryUtil.memFree(ubo);
		}
	}

	/** IR-flagged light: never in the visible sum under SPLIT, always in {@code _grl_irSum}; MERGED adds it back. */
	@Test
	void irLightsSplitFromVisible() {
		try (HeadlessGl gl = HeadlessGl.createOrSkip()) {
			List<LightFrame.Light> lights = lights();
			lights.get(0).ir = true;
			List<LightFrame.Light> visible = lights.subList(1, lights.size());
			List<LightFrame.Light> ir = lights.subList(0, 1);

			Random random = new Random(3);
			float[][] ps = new float[POINTS][];
			float[][] ns = new float[POINTS][];
			for (int i = 0; i < POINTS; i++) {
				LightFrame.Light near = lights.get(i % 2 == 0 ? 0 : 1 + i % (lights.size() - 1));
				float spread = near.range * 1.1f;
				ps[i] = new float[] {near.rx + (random.nextFloat() * 2 - 1) * spread,
						near.ry + (random.nextFloat() * 2 - 1) * spread, near.rz + (random.nextFloat() * 2 - 1) * spread};
				ns[i] = new float[3];
			}
			int buffer = glGenBuffers();
			for (Lights.IrView mode : new Lights.IrView[] {Lights.IrView.SPLIT, Lights.IrView.MERGED}) {
				ByteBuffer ubo = frame(lights, mode);
				bindUbo(buffer, ubo);
				float[] out = shade(gl, ps, ns);
				int irLit = 0;
				for (int i = 0; i < POINTS; i++) {
					double[] seen = reference(mode == Lights.IrView.MERGED ? lights : visible, ps[i], ns[i]);
					double[] irOnly = reference(ir, ps[i], ns[i]);
					for (int c = 0; c < 3; c++) {
						assertThat((double) out[i * 8 + c]).as("%s colour sample %d channel %d", mode, i, c)
								.isCloseTo(seen[c], org.assertj.core.data.Offset.offset(1.0e-4 + 1.0e-3 * seen[c]));
						assertThat((double) out[i * 8 + 4 + c]).as("%s IR sample %d channel %d", mode, i, c)
								.isCloseTo(irOnly[c], org.assertj.core.data.Offset.offset(1.0e-4 + 1.0e-3 * irOnly[c]));
					}
					if (irOnly[0] > 1.0e-3) {
						irLit++;
					}
				}
				assertThat(irLit).as("%s samples lit by the IR light", mode).isGreaterThan(POINTS / 8);
				MemoryUtil.memFree(ubo);
			}
			glDeleteBuffers(buffer);
		}
	}

	/**
	 * Patched {@code rendertype_solid}, all-zero lightmap (darkness mod), white albedo, point light 2 blocks in
	 * front: unlit => exactly black; visible light => lit; IR light under SPLIT => black colour, lit COLOR1;
	 * night sky => COLOR1 under SPLIT, colour under MERGED, scaled by sky level.
	 */
	@Test
	void zeroLightmapStillLit() {
		try (HeadlessGl gl = HeadlessGl.createOrSkip()) {
			int program = glCreateProgram();
			int vertex = shader("solid", GL_VERTEX_SHADER, LightShaders.vanilla("rendertype_solid", true,
					vanilla("rendertype_solid.vsh")));
			int fragment = shader("solid", GL_FRAGMENT_SHADER, LightShaders.vanilla("rendertype_solid", false,
					vanilla("rendertype_solid.fsh")));
			glAttachShader(program, vertex);
			glAttachShader(program, fragment);
			glLinkProgram(program);
			assertThat(glGetProgrami(program, GL_LINK_STATUS)).as(glGetProgramInfoLog(program)).isEqualTo(GL_TRUE);
			glUniformBlockBinding(program, glGetUniformBlockIndex(program, "GemRenderLights"), LightFrame.UBO_BINDING);
			glUseProgram(program);
			float[] identity = new org.joml.Matrix4f().get(new float[16]);
			glUniformMatrix4fv(glGetUniformLocation(program, "ModelViewMat"), false, identity);
			glUniformMatrix4fv(glGetUniformLocation(program, "ProjMat"), false, identity);
			glUniform4f(glGetUniformLocation(program, "ColorModulator"), 1, 1, 1, 1);
			glUniform1f(glGetUniformLocation(program, "FogStart"), 1.0e4f);
			glUniform1f(glGetUniformLocation(program, "FogEnd"), 2.0e4f);
			HeadlessGl.samplerUnit(program, "Sampler0", 0);
			HeadlessGl.samplerUnit(program, "Sampler2", 2);
			HeadlessGl.samplerUnit(program, "_grl_grid", LightGrid.UNIT);
			HeadlessGl.samplerUnit(program, "_grl_cookies", LightCookies.UNIT);
			HeadlessGl.samplerUnit(program, "_grl_shadows", LightShadows.UNIT);

			int white = texture(1, 1, 1.0f);
			int dark = texture(16, 16, 0.0f);
			int[] targets = {texture(8, 8, 0.0f), texture(8, 8, 0.0f)};
			glActiveTexture(GL_TEXTURE0);
			glBindTexture(GL_TEXTURE_2D, white);
			glActiveTexture(GL_TEXTURE2);
			glBindTexture(GL_TEXTURE_2D, dark);
			glActiveTexture(GL_TEXTURE0);

			int vao = glGenVertexArrays();
			glBindVertexArray(vao);
			int vbo = glGenBuffers();
			glBindBuffer(GL_ARRAY_BUFFER, vbo);
			glBufferData(GL_ARRAY_BUFFER, new float[] {-1, -1, 0, 3, -1, 0, -1, 3, 0}, GL_STATIC_DRAW);
			int position = glGetAttribLocation(program, "Position");
			glEnableVertexAttribArray(position);
			glVertexAttribPointer(position, 3, GL_FLOAT, false, 0, 0L);
			glVertexAttrib4f(glGetAttribLocation(program, "Color"), 1, 1, 1, 1);
			glVertexAttrib2f(glGetAttribLocation(program, "UV0"), 0.5f, 0.5f);
			glVertexAttribI2i(glGetAttribLocation(program, "UV2"), 0, 0);
			glVertexAttrib3f(glGetAttribLocation(program, "Normal"), 0, 0, 1);

			int fbo = glGenFramebuffers();
			glBindFramebuffer(GL_FRAMEBUFFER, fbo);
			glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, targets[0], 0);
			glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT1, GL_TEXTURE_2D, targets[1], 0);
			glDrawBuffers(new int[] {GL_COLOR_ATTACHMENT0, GL_COLOR_ATTACHMENT1});
			glViewport(0, 0, 8, 8);

			int buffer = glGenBuffers();
			float[][] none = draw(buffer, List.of(), Lights.IrView.SPLIT, 0.0f);
			LightFrame.Light visible = point(EYE.x, EYE.y, EYE.z + 2.0, 10.0f, 0xFFFFFF, 5.0f);
			visible.relativeTo(EYE);
			float[][] lit = draw(buffer, List.of(visible), Lights.IrView.SPLIT, 0.0f);
			LightFrame.Light ir = point(EYE.x, EYE.y, EYE.z + 2.0, 10.0f, 0xFFFFFF, 5.0f);
			ir.ir = true;
			ir.relativeTo(EYE);
			float[][] split = draw(buffer, List.of(ir), Lights.IrView.SPLIT, 0.0f);
			float[][] merged = draw(buffer, List.of(ir), Lights.IrView.MERGED, 0.0f);
			float[][] roofed = draw(buffer, List.of(), Lights.IrView.SPLIT, 0.5f);
			glVertexAttribI2i(glGetAttribLocation(program, "UV2"), 0, 240);
			float[][] open = draw(buffer, List.of(), Lights.IrView.SPLIT, 0.5f);
			float[][] openMerged = draw(buffer, List.of(), Lights.IrView.MERGED, 0.5f);

			assertThat(none[0]).as("unlit, zero lightmap").containsOnly(0.0f);
			assertThat(lit[0][0]).as("visible light on zero lightmap").isGreaterThan(0.5f);
			assertThat(lit[1][0]).as("visible light, IR attachment").isEqualTo(0.0f);
			assertThat(split[0][0]).as("IR light, colour under SPLIT").isEqualTo(0.0f);
			assertThat(split[1][0]).as("IR light, IR attachment").isCloseTo(lit[0][0], org.assertj.core.data.Offset.offset(1.0e-3f));
			assertThat(merged[0][0]).as("IR light, colour under MERGED").isCloseTo(lit[0][0], org.assertj.core.data.Offset.offset(1.0e-3f));
			assertThat(roofed[1]).as("night sky, sky level 0").containsOnly(0.0f);
			assertThat(open[0]).as("night sky not in colour under SPLIT").containsOnly(0.0f);
			assertThat(open[1][0]).as("night sky, sky level 15").isCloseTo(0.5f, org.assertj.core.data.Offset.offset(1.0e-3f));
			assertThat(openMerged[0][0]).as("night sky, MERGED colour").isCloseTo(0.5f, org.assertj.core.data.Offset.offset(1.0e-3f));

			glBindFramebuffer(GL_FRAMEBUFFER, 0);
			glDeleteFramebuffers(fbo);
			glDeleteTextures(targets);
			glDeleteTextures(new int[] {white, dark});
			glDeleteBuffers(new int[] {vbo, buffer});
			glDeleteVertexArrays(vao);
			glDeleteProgram(program);
		}
	}

	/** Centre texel {rgb of COLOR0, rgb of COLOR1} after one full-viewport draw. */
	private static float[][] draw(int buffer, List<LightFrame.Light> lights, Lights.IrView mode, float night) {
		ByteBuffer ubo = frame(lights, mode, night);
		bindUbo(buffer, ubo);
		MemoryUtil.memFree(ubo);
		glClearBufferfv(GL_COLOR, 0, new float[4]);
		glClearBufferfv(GL_COLOR, 1, new float[4]);
		glDrawArrays(GL_TRIANGLES, 0, 3);
		float[][] out = new float[2][4];
		for (int i = 0; i < 2; i++) {
			glReadBuffer(GL_COLOR_ATTACHMENT0 + i);
			glReadPixels(4, 4, 1, 1, GL_RGBA, GL_FLOAT, out[i]);
		}
		return new float[][] {java.util.Arrays.copyOf(out[0], 3), java.util.Arrays.copyOf(out[1], 3)};
	}

	private static int texture(int w, int h, float value) {
		float[] texels = new float[w * h * 4];
		java.util.Arrays.fill(texels, value);
		int texture = glGenTextures();
		glBindTexture(GL_TEXTURE_2D, texture);
		glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F, w, h, 0, GL_RGBA, GL_FLOAT, texels);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
		return texture;
	}

	/** {@link IrTarget}'s premise: a program without a location-1 output leaves COLOR1 untouched. */
	@Test
	void unwrittenLocationKeepsContents() {
		try (HeadlessGl gl = HeadlessGl.createOrSkip()) {
			int program = glCreateProgram();
			int vertex = shader("fill", GL_VERTEX_SHADER, """
					#version 330 core
					void main() {
					    gl_Position = vec4(vec2(gl_VertexID == 1 ? 3.0 : -1.0, gl_VertexID == 2 ? 3.0 : -1.0), 0.0, 1.0);
					}
					""");
			int fragment = shader("fill", GL_FRAGMENT_SHADER, """
					#version 330 core
					layout(location = 0) out vec4 colour;
					void main() {
					    colour = vec4(1.0);
					}
					""");
			glAttachShader(program, vertex);
			glAttachShader(program, fragment);
			glLinkProgram(program);
			glUseProgram(program);
			int vao = glGenVertexArrays();
			glBindVertexArray(vao);
			int[] targets = {texture(8, 8, 0.0f), texture(8, 8, 0.0f)};
			int fbo = glGenFramebuffers();
			glBindFramebuffer(GL_FRAMEBUFFER, fbo);
			glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, targets[0], 0);
			glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT1, GL_TEXTURE_2D, targets[1], 0);
			glDrawBuffers(new int[] {GL_COLOR_ATTACHMENT0, GL_COLOR_ATTACHMENT1});
			glViewport(0, 0, 8, 8);
			glClearBufferfv(GL_COLOR, 1, new float[] {0.25f, 0.5f, 0.75f, 1.0f});
			glDrawArrays(GL_TRIANGLES, 0, 3);
			float[] colour = new float[4];
			float[] kept = new float[4];
			glReadBuffer(GL_COLOR_ATTACHMENT0);
			glReadPixels(4, 4, 1, 1, GL_RGBA, GL_FLOAT, colour);
			glReadBuffer(GL_COLOR_ATTACHMENT1);
			glReadPixels(4, 4, 1, 1, GL_RGBA, GL_FLOAT, kept);
			assertThat(colour).containsOnly(1.0f);
			assertThat(kept).containsExactly(0.25f, 0.5f, 0.75f, 1.0f);
			glBindFramebuffer(GL_FRAMEBUFFER, 0);
			glDeleteFramebuffers(fbo);
			glDeleteTextures(targets);
			glDeleteVertexArrays(vao);
			glDeleteProgram(program);
		}
	}

	private static final Set<String> VANILLA = Set.of("rendertype_solid", "rendertype_cutout_mipped",
			"rendertype_cutout", "rendertype_translucent", "rendertype_tripwire", "rendertype_entity_solid",
			"rendertype_entity_cutout", "rendertype_entity_cutout_no_cull", "rendertype_entity_cutout_no_cull_z_offset",
			"rendertype_entity_translucent", "rendertype_entity_translucent_cull", "rendertype_entity_smooth_cutout",
			"rendertype_entity_no_outline", "rendertype_entity_decal", "rendertype_item_entity_translucent_cull",
			"rendertype_armor_cutout_no_cull", "particle");

	private static final Pattern IMPORT = Pattern.compile("#moj_import <(\\w+\\.glsl)>");

	private static String vanilla(String file) {
		Matcher matcher = IMPORT.matcher(ShaderSources.read("assets/minecraft/shaders/core/" + file));
		StringBuilder out = new StringBuilder();
		while (matcher.find()) {
			matcher.appendReplacement(out, Matcher.quoteReplacement(
					ShaderSources.read("assets/minecraft/shaders/include/" + matcher.group(1))
							.replaceAll("(?m)^#version.*$", "")));
		}
		return matcher.appendTail(out).toString();
	}

	@Test
	void patchedVanillaProgramsLink() {
		try (HeadlessGl gl = HeadlessGl.createOrSkip()) {
			for (String name : VANILLA) {
				int program = glCreateProgram();
				int vertex = shader(name, GL_VERTEX_SHADER, LightShaders.vanilla(name, true, vanilla(name + ".vsh")));
				int fragment = shader(name, GL_FRAGMENT_SHADER, LightShaders.vanilla(name, false, vanilla(name + ".fsh")));
				glAttachShader(program, vertex);
				glAttachShader(program, fragment);
				glLinkProgram(program);
				assertThat(glGetProgrami(program, GL_LINK_STATUS)).as(name + ": " + glGetProgramInfoLog(program))
						.isEqualTo(GL_TRUE);
				assertThat(glGetUniformBlockIndex(program, "GemRenderLights")).as(name).isNotEqualTo(GL_INVALID_INDEX);
				glDeleteShader(vertex);
				glDeleteShader(fragment);
				glDeleteProgram(program);
			}
		}
	}

	/** Lightmap call as Veil reprints it (parenthesised operand) and as Sable rewrites it (scaled ivec2 argument). */
	@Test
	void rewrittenLightmapCallsStillPatch() {
		try (HeadlessGl gl = HeadlessGl.createOrSkip()) {
			for (String name : VANILLA) {
				String source = vanilla(name + ".vsh").replace("texelFetch(Sampler2, UV2 / 16, 0)", "texelFetch(Sampler2, (UV2 / 16), 0)")
						.replace("minecraft_sample_lightmap(Sampler2, UV2)",
								"minecraft_sample_lightmap(Sampler2, ivec2((UV2 * vec2(1.0, 1.0))))");
				String patched = LightShaders.vanilla(name, true, source);
				assertThat(patched).as(name).containsAnyOf("_grl_lm(texelFetch(Sampler2, (UV2 / 16), 0), clamp(float(((UV2 / 16)).y)",
						"_grl_lm(minecraft_sample_lightmap(Sampler2, ivec2((UV2 * vec2(1.0, 1.0)))), clamp(float((ivec2((UV2 * vec2(1.0, 1.0)))).y)");
				int program = glCreateProgram();
				int vertex = shader(name, GL_VERTEX_SHADER, patched);
				int fragment = shader(name, GL_FRAGMENT_SHADER, LightShaders.vanilla(name, false, vanilla(name + ".fsh")));
				glAttachShader(program, vertex);
				glAttachShader(program, fragment);
				glLinkProgram(program);
				assertThat(glGetProgrami(program, GL_LINK_STATUS)).as(name + ": " + glGetProgramInfoLog(program))
						.isEqualTo(GL_TRUE);
				glDeleteShader(vertex);
				glDeleteShader(fragment);
				glDeleteProgram(program);
			}
		}
	}

	private static int shader(String name, int type, String source) {
		int shader = glCreateShader(type);
		glShaderSource(shader, source);
		glCompileShader(shader);
		assertThat(glGetShaderi(shader, GL_COMPILE_STATUS)).as(name + ": " + glGetShaderInfoLog(shader))
				.isEqualTo(GL_TRUE);
		return shader;
	}
}
