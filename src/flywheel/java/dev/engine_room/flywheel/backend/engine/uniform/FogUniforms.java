package dev.engine_room.flywheel.backend.engine.uniform;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.renderer.fog.FogData;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * 26.1 took the fog scalars off {@code RenderSystem}: vanilla now packs them into a UBO owned by a
 * private {@code GameRenderer#fogRenderer} and hands shaders an opaque {@code GpuBufferSlice}. Flywheel
 * has its own uniform block, so it catches the fog on its way past instead --
 * {@link ViewportEvent.RenderFog} fires with the mutable {@link FogData} every frame, which is the
 * supported hook and also the one a fog-modifying mod would use.
 */
public final class FogUniforms extends UniformWriter {
	private static final int SIZE = 4 * 7;
	static final UniformBuffer BUFFER = new UniformBuffer(Uniforms.FOG_INDEX, SIZE);

	/**
	 * 26.1 dropped the sphere/cylinder distinction entirely -- there is no FogShape any more. Flywheel's
	 * GLSL still reads the index, so keep writing what used to be SPHERE until the fog shaders are
	 * ported to 26.1's environmental/render-distance split.
	 */
	private static final int FOG_SHAPE_SPHERE = 0;

	@Nullable
	private static FogData lastFog;

	private FogUniforms() {
	}

	public static void onRenderFog(ViewportEvent.RenderFog event) {
		lastFog = event.getFogData();
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
		// environmentalStart/End are what ViewportEvent.RenderFog calls the near and far plane, and are
		// the pair that corresponds to the old single fog range.
		ptr = writeFloat(ptr, fog.environmentalStart);
		ptr = writeFloat(ptr, fog.environmentalEnd);
		ptr = writeInt(ptr, FOG_SHAPE_SPHERE);

		BUFFER.markDirty();
	}
}
