package com.wf.gemrender.volume;

import static org.lwjgl.opengl.GL20C.GL_MAX_TEXTURE_IMAGE_UNITS;
import static org.lwjgl.opengl.GL33C.glGetInteger;

import com.wf.gemrender.GemRender;
import com.wf.gemrender.water.GpuStampTimer;

public final class Volumetrics {
	private static final boolean ENABLED =
			!"false".equalsIgnoreCase(System.getProperty("gemrender.volumetrics"));

	private static final int REQUIRED_UNITS = Math.max(
			Math.max(VolumeBuffer.TEXTURE_UNIT, VolumeNoise.TEXTURE_UNIT),
			Math.max(SceneDepth.TEXTURE_UNIT, VolumeAtlas.TEXTURE_UNIT)) + 1;

	private static final Volumetrics INSTANCE = new Volumetrics();

	private Boolean supported;

	private boolean present;

	private long framesPresent;

	private int volumes;

	private final GpuStampTimer depthTimer = new GpuStampTimer();

	private long depthCpuNanos;

	private long depthCpuSamples;

	private Volumetrics() {
	}

	public static Volumetrics getInstance() {
		return INSTANCE;
	}

	public boolean available() {
		if (!ENABLED) {
			return false;
		}
		if (supported == null) {
			int units = glGetInteger(GL_MAX_TEXTURE_IMAGE_UNITS);
			supported = units >= REQUIRED_UNITS;
			if (!supported) {
				GemRender.LOGGER.warn("Volumetrics disabled: raymarched volumes need {} fragment texture "
						+ "units and this driver reports {}. Everything else is unaffected.",
						REQUIRED_UNITS, units);
			}
		}
		return supported;
	}

	public void upload() {
		if (!available() || VolumeBuffer.getInstance()
				.activeCount() == 0) {
			return;
		}

		VolumeBuffer.getInstance()
				.uploadAndBind();
		VolumeNoise.getInstance()
				.bind();
		VolumeAtlas atlas = VolumeAtlas.getInstance();
		atlas.flushPending();
		atlas.bind();
	}

	public void beginFrame() {
		volumes = VolumeBuffer.getInstance()
				.activeCount();
		present = available() && volumes > 0;

		if (!present) {
			return;
		}

		framesPresent++;

		long cpuStart = System.nanoTime();
		depthTimer.begin();
		SceneDepth.getInstance()
				.capture();
		depthTimer.end();
		depthCpuNanos += System.nanoTime() - cpuStart;
		depthCpuSamples++;
	}

	public boolean present() {
		return present;
	}

	public int volumes() {
		return volumes;
	}

	public void resetRun() {
		framesPresent = 0;
		depthCpuNanos = 0;
		depthCpuSamples = 0;
		depthTimer.reset();
	}

	public String report() {
		if (!ENABLED) {
			return "off";
		}
		if (supported != null && !supported) {
			return "unsupported";
		}

		String depth = "depthGpu=" + depthTimer.meanMicros() + "us,depthCpu="
				+ (depthCpuSamples == 0 ? 0 : depthCpuNanos / depthCpuSamples / 1000) + "us";
		if (framesPresent == 0) {
			return "idle(" + depth + ")";
		}
		return "active(frames=" + framesPresent + ",volumes=" + volumes + "," + depth + ")";
	}
}
