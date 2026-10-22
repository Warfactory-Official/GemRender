package dev.engine_room.flywheel.backend.engine.uniform;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.renderer.fog.FogData;

/**
 * 26.1 took the fog scalars off {@code RenderSystem}: vanilla now packs them into a UBO owned by a
 * private {@code GameRenderer#fogRenderer} and hands shaders an opaque {@code GpuBufferSlice}. Flywheel
 * has its own uniform block, so it catches the fog on its way past instead: NeoForge's
 * {@code ViewportEvent.RenderFog} on NeoForge, the camera render state's {@link FogData} on Fabric.
 * The block is written from it each frame by {@link Uniforms#update}.
 */
public final class FogUniforms extends UniformWriter {
	private static final int SIZE = 4 * 9;
	static final UniformBuffer BUFFER = new UniformBuffer(Uniforms.FOG_INDEX, SIZE);

	/**
	 * 26.1 dropped the sphere/cylinder distinction: the environmental range is spherical and the
	 * render-distance range cylindrical, both always. Flywheel's GLSL still reads the index.
	 */
	private static final int FOG_SHAPE_SPHERE = 0;

	@Nullable
	private static FogData lastFog;

	private FogUniforms() {
	}

	public static void onRenderFog(FogData fog) {
		lastFog = fog;
	}

	public static void update() {
		FogData fog = lastFog;
		if (fog == null) {
			return;
		}

		long ptr = BUFFER.ptr();

		ptr = writeFloat(ptr, fog.color.x);
		ptr = writeFloat(ptr, fog.color.y);
		ptr = writeFloat(ptr, fog.color.z);
		ptr = writeFloat(ptr, fog.color.w);
		ptr = writeFloat(ptr, fog.environmentalStart);
		ptr = writeFloat(ptr, fog.environmentalEnd);
		ptr = writeFloat(ptr, fog.renderDistanceStart);
		ptr = writeFloat(ptr, fog.renderDistanceEnd);
		ptr = writeInt(ptr, FOG_SHAPE_SPHERE);

		BUFFER.markDirty();
	}
}
