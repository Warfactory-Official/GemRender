package com.wf.gemrender.spike;

import com.wf.gemrender.particle.GemRenderParticleTypes;
import com.wf.gemrender.particle.ParticleEmitter;
import com.wf.gemrender.particle.ParticleModels;
import com.wf.gemrender.particle.ParticlePool;

import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.api.visual.EffectVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.visual.AbstractVisual;
import dev.engine_room.flywheel.lib.visual.SimpleTickableVisual;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public final class ParticleSpikeVisual extends AbstractVisual
		implements EffectVisual<ParticleSpikeEffect>, SimpleTickableVisual {
	public static final String BLEND = System.getProperty("gemrender.particleblend", "translucent");

	private static final ResourceLocation TEXTURE =
			ResourceLocation.withDefaultNamespace("textures/particle/big_smoke_4.png");

	private final ParticleSpikeEffect effect;

	private final ParticlePool[] pools;

	public ParticleSpikeVisual(VisualizationContext ctx, ParticleSpikeEffect effect, float partialTick) {
		super(ctx, (Level) effect.level(), partialTick);
		this.effect = effect;

		Model model = switch (BLEND) {
			case "additive" -> ParticleModels.additive(TEXTURE);
			case "cutout" -> ParticleModels.cutout(TEXTURE);
			case "absorbance" -> ParticleModels.absorbance(TEXTURE);
			default -> ParticleModels.translucent(TEXTURE);
		};

		ParticleEmitter[] emitters = effect.emitters();
		pools = new ParticlePool[emitters.length];
		for (int i = 0; i < emitters.length; i++) {
			pools[i] = new ParticlePool(ctx, emitters[i], GemRenderParticleTypes.BILLBOARD, model);
		}
	}

	@Override
	public void tick(Context context) {
		effect.emit();
	}

	@Override
	protected void _delete() {
		for (ParticlePool pool : pools) {
			pool.delete();
		}
	}
}
