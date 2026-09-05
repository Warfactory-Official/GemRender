package com.wf.gemrender.spike;

import com.wf.gemrender.volume.VolumeQuality;
import com.wf.gemrender.volume.VolumeStyle;

import dev.engine_room.flywheel.api.visual.Effect;
import dev.engine_room.flywheel.api.visual.EffectVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

/**
 * A grid of raymarched gas volumes, the volumetric counterpart to {@link ParticleSpikeEffect}.
 *
 * <p>The point of the row is that its cost does not scale the way the particle row's does. A particle
 * fountain pays per quad; a volume pays per screen pixel it covers times its step count, and nothing else.
 * So the interesting comparison is not "same particle count" but "same screen coverage", which is why the
 * default box is sized to roughly match what {@code -Pparticles} fills.
 */
public final class VolumeSpikeEffect implements Effect {

	public static final float SIZE =
			Float.parseFloat(System.getProperty("gemrender.volumesize", "6"));

	public static final float DENSITY =
			Float.parseFloat(System.getProperty("gemrender.volumedensity", "1.2"));

	private static final int TINT =
			Integer.parseInt(System.getProperty("gemrender.volumetint", "C8D2C0"), 16);

	private static final int SPACING = Integer.getInteger("gemrender.volumespacing", 20);

	private static final float PHASE =
			Float.parseFloat(System.getProperty("gemrender.volumephase", "0.3"));

	private static final float AMBIENT =
			Float.parseFloat(System.getProperty("gemrender.volumeambient", "0.35"));

	private static final float SUN =
			Float.parseFloat(System.getProperty("gemrender.volumesun", "0.3"));

	private static final float EDGE =
			Float.parseFloat(System.getProperty("gemrender.volumeedge", "0.35"));

	private static final float DETAIL =
			Float.parseFloat(System.getProperty("gemrender.volumedetail", "0.6"));

	private static final VolumeQuality QUALITY =
			VolumeQuality.parse(System.getProperty("gemrender.volumequality"));

	private final Level level;

	private final BlockPos origin;

	private final int count;

	public VolumeSpikeEffect(Level level, BlockPos origin, int count) {
		this.level = level;
		this.origin = origin;
		this.count = Math.max(1, count);
	}

	public BlockPos origin() {
		return origin;
	}

	public int count() {
		return count;
	}

	public int spacing() {
		return SPACING;
	}

	public static VolumeStyle style() {
		return VolumeStyle.builder()
				.density(DENSITY)
				.tint(TINT)
				.detail(DETAIL)
				.edge(EDGE)
				.rise(0.4f)
				.phase(PHASE)
				.ambient(AMBIENT)
				.sunStrength(SUN)
				.quality(QUALITY)
				.build();
	}

	public static float size() {
		return SIZE;
	}

	@Override
	public LevelAccessor level() {
		return level;
	}

	@Override
	public EffectVisual<?> visualize(VisualizationContext ctx, float partialTick) {
		return new VolumeSpikeVisual(ctx, this, partialTick);
	}
}
