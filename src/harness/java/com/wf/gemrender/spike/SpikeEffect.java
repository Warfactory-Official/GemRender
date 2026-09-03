package com.wf.gemrender.spike;

import dev.engine_room.flywheel.api.visual.Effect;
import dev.engine_room.flywheel.api.visual.EffectVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;

public record SpikeEffect(LevelAccessor level, BlockPos origin, int count) implements Effect {
	@Override
	public EffectVisual<?> visualize(VisualizationContext ctx, float partialTick) {
		return new SpikeVisual(ctx, this, partialTick);
	}
}
