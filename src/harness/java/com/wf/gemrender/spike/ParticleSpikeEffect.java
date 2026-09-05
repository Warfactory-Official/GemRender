package com.wf.gemrender.spike;

import com.wf.gemrender.particle.ParticleBuffer;
import com.wf.gemrender.particle.ParticleEmitter;
import com.wf.gemrender.particle.ParticleStyle;

import dev.engine_room.flywheel.api.visual.Effect;
import dev.engine_room.flywheel.api.visual.EffectVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

public final class ParticleSpikeEffect implements Effect {
	public static final float LIFE_SECONDS = 3.0f;

	public static final int EMITTERS = Integer.getInteger("gemrender.particleemitters", 1);

	public static final float SIZE_SCALE =
			Float.parseFloat(System.getProperty("gemrender.particlesize", "1"));

	private static final float LIFE_JITTER = 0.3f;

	private static final float MEAN_LIFE_FRACTION = 1.0f - LIFE_JITTER * 0.5f;

	private static final int SPACING = Integer.getInteger("gemrender.particlespacing", 4);


	private static final Object STYLE_LOCK = new Object();

	private static int styleIndex = -1;

	private final Level level;

	private final BlockPos origin;

	private final int count;

	private final ParticleEmitter[] emitters;

	private final BlockPos[] nozzles;

	private final float[] budgets;

	private final RandomSource random = RandomSource.create(0x9E3779B9L);

	public ParticleSpikeEffect(Level level, BlockPos origin, int count) {
		this(level, origin, count, EMITTERS);
	}

	public ParticleSpikeEffect(Level level, BlockPos origin, int count, int emitterCount) {
		this.level = level;
		this.origin = origin;
		this.count = count;

		int n = Math.max(1, emitterCount);
		int side = (int) Math.ceil(Math.sqrt(n));
		int per = Math.max(1, count / n);

		emitters = new ParticleEmitter[n];
		nozzles = new BlockPos[n];
		budgets = new float[n];

		for (int i = 0; i < n; i++) {
			BlockPos at = origin.offset((i % side - side / 2) * SPACING, 0, (i / side - side / 2) * SPACING);
			nozzles[i] = at;
			emitters[i] = ParticleEmitter.create(style(), per,
					at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5);
		}
	}

	private static final int TINT =
			Integer.parseInt(System.getProperty("gemrender.particletint", "FF7A28"), 16);

	private static final String LIGHT = System.getProperty("gemrender.particlelight", "");

	private static final boolean COOL =
			!"false".equalsIgnoreCase(System.getProperty("gemrender.particlecool"));

	public static int style() {
		synchronized (STYLE_LOCK) {
			if (styleIndex < 0) {
				ParticleStyle.Builder builder = ParticleStyle.builder()
						.drag(ParticleStyle.dragFromPerTickFactor(0.93f),
							ParticleStyle.dragFromPerTickFactor(0.97f))
						.gravity(-1.6f)
						.size(0.4f, 2.4f)
						.tint(TINT)
						.alpha(0.35f, 0.6f)
						.fadeIn(0.05f)
						.spin(0.6f);

				if (COOL) {
					builder.cool(0.1f, 0.6f);
				}
				if (!LIGHT.isEmpty()) {
					String[] parts = LIGHT.split(",");
					builder.light(Integer.parseInt(parts[0].trim()) / 16.0f,
							Integer.parseInt(parts[parts.length - 1].trim()) / 16.0f);
				}

				styleIndex = ParticleBuffer.getInstance()
						.registerStyle(builder.build());
			}
			return styleIndex;
		}
	}

	public BlockPos origin() {
		return origin;
	}

	public int count() {
		return count;
	}

	public ParticleEmitter[] emitters() {
		return emitters;
	}

	public void emit() {
		float rate = (count / (float) emitters.length) / (LIFE_SECONDS * MEAN_LIFE_FRACTION * 20.0f);

		for (int i = 0; i < emitters.length; i++) {
			budgets[i] += rate;

			BlockPos at = nozzles[i];
			ParticleEmitter emitter = emitters[i];

			while (budgets[i] >= 1.0f) {
				budgets[i] -= 1.0f;

				double angle = random.nextDouble() * Math.PI * 2.0;
				double spread = random.nextDouble() * 1.5;

				emitter.spawn(at.getX() + 0.5 + random.nextGaussian() * 0.2,
						at.getY() + 1.0,
						at.getZ() + 0.5 + random.nextGaussian() * 0.2,
						Math.cos(angle) * spread,
						3.0 + random.nextDouble() * 2.0,
						Math.sin(angle) * spread,
						LIFE_SECONDS * (1.0f - LIFE_JITTER + random.nextFloat() * LIFE_JITTER),
						(0.7f + random.nextFloat() * 0.6f) * SIZE_SCALE,
						random.nextFloat() * 6.2831855f,
						0.8f + random.nextFloat() * 0.4f);
			}
		}
	}

	@Override
	public LevelAccessor level() {
		return level;
	}

	@Override
	public EffectVisual<?> visualize(VisualizationContext ctx, float partialTick) {
		return new ParticleSpikeVisual(ctx, this, partialTick);
	}
}
