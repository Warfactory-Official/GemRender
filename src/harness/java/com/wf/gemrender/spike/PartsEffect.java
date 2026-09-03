package com.wf.gemrender.spike;

import dev.engine_room.flywheel.api.visual.Effect;
import dev.engine_room.flywheel.api.visual.EffectVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LevelAccessor;

public record PartsEffect(LevelAccessor level, BlockPos origin, ResourceLocation asset, int count,
		String animation, boolean sync, float spin, String spinBone, float spinDuty) implements Effect {
	@Override
	public EffectVisual<?> visualize(VisualizationContext ctx, float partialTick) {
		return new PartsVisual(ctx, this, partialTick);
	}
}
